package com.example.kiwi_backend.payment.application;

import com.example.kiwi_backend.common.exception.InsufficientGemException;
import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.GemResult;
import com.example.kiwi_backend.contract.dto.MemberSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.payment.api.dto.PaymentApproveResponse;
import com.example.kiwi_backend.payment.api.dto.PaymentHistoryResponse;
import com.example.kiwi_backend.payment.api.dto.PaymentReadyResponse;
import com.example.kiwi_backend.payment.api.dto.PaymentRefundResponse;
import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.entity.ShopProduct;
import com.example.kiwi_backend.payment.domain.enumerated.PaymentStatus;
import com.example.kiwi_backend.payment.domain.repository.PaymentRepository;
import com.example.kiwi_backend.payment.domain.repository.ShopProductRepository;
import com.example.kiwi_backend.payment.infra.kakaopay.KakaoPayApproval;
import com.example.kiwi_backend.payment.infra.kakaopay.KakaoPayClient;
import com.example.kiwi_backend.payment.infra.kakaopay.KakaoPayReady;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 카카오페이로 보석 구매·환불.
 *
 * 동시성: 결제 행을 먼저 잠그고 카카오페이 호출과 보석 변동을 같은 트랜잭션에서 한다.
 * 같은 주문에 승인 요청이 두 번 와도 두 번째는 첫 번째가 끝날 때까지 기다렸다가 PAID를 보고 그대로 돌아간다.
 * 잠금 순서는 항상 결제 → 지갑. 카카오페이 응답을 기다리는 동안(최대 수 초) 이 잠금을 쥐고 있다.
 *
 * 우리 DB와 카카오페이가 어긋나는 경우:
 * - 승인: 카카오페이 승인 후 우리 쪽 처리(금액 검증, 보석 지급, 커밋)가 실패하면 카카오페이 결제를 바로 취소한다.
 *   그래서 승인 트랜잭션은 커밋 실패까지 잡을 수 있도록 @Transactional 대신 TransactionTemplate으로 감싼다.
 * - 환불: 보석 회수 → 카카오페이 취소 순서. 카카오페이 취소가 실패하면 보석 회수도 함께 롤백된다.
 *   취소 성공 뒤 커밋만 실패하는 경우는 자동 복구하지 않고 에러 로그를 남긴다.
 */
@Slf4j
@Service
public class PaymentService {

    private static final int MAX_HISTORY_PAGE_SIZE = 50;
    private static final List<PaymentStatus> VISIBLE_STATUSES = List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final ShopProductRepository productRepository;
    private final MemberQueryService memberQueryService;
    private final GemWallet gemWallet;
    private final KakaoPayClient kakaoPayClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration refundWindow;

    public PaymentService(PaymentRepository paymentRepository,
                          ShopProductRepository productRepository,
                          MemberQueryService memberQueryService,
                          GemWallet gemWallet,
                          KakaoPayClient kakaoPayClient,
                          PlatformTransactionManager transactionManager,
                          Clock clock,
                          @Value("${app.payment.refund-window:7d}") Duration refundWindow) {
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.memberQueryService = memberQueryService;
        this.gemWallet = gemWallet;
        this.kakaoPayClient = kakaoPayClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.refundWindow = refundWindow;
    }

    // ===== 결제 준비 =====

    /** 결제 행을 만들고 카카오페이 결제창 URL을 받는다. 카카오페이 호출이 실패하면 결제 행도 남지 않는다 */
    @Transactional
    public PaymentReadyResponse ready(Long memberId, Long productId) {
        MemberSummary member = memberQueryService.getSummary(memberId);
        if (!member.active()) {
            throw new IllegalStateException("이용할 수 없는 계정입니다");
        }

        ShopProduct product = productRepository.findById(productId)
                .filter(ShopProduct::isActive)
                .orElseThrow(() -> new NotFoundException("판매 중인 상품이 아닙니다"));

        Payment payment = paymentRepository.save(Payment.ready(memberId, product, UUID.randomUUID().toString()));

        KakaoPayReady ready = kakaoPayClient.ready(payment.getOrderId(), memberId, product.getName(), payment.getAmount());
        payment.attachPaymentKey(ready.tid());

        return new PaymentReadyResponse(
                payment.getOrderId(),
                payment.getGrantedGem(),
                ready.pcUrl(),
                ready.mobileUrl(),
                ready.appUrl()
        );
    }

    // ===== 결제 승인 =====

    /**
     * 사용자가 결제창에서 승인한 뒤 프론트가 pg_token을 넘겨 부른다.
     * 이미 승인된 주문이면 다시 지급하지 않고 같은 결과를 돌려준다 (새로고침, 중복 클릭).
     */
    public PaymentApproveResponse approve(Long memberId, String orderId, String pgToken) {
        AtomicBoolean requested = new AtomicBoolean(false);
        AtomicReference<Approved> approved = new AtomicReference<>();
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = findOwnedForUpdate(memberId, orderId);

                if (payment.getStatus() == PaymentStatus.PAID) {
                    GemResult result = gemWallet.apply(purchaseCommand(payment));   // 멱등 키라 다시 지급되지 않음
                    return new PaymentApproveResponse(orderId, payment.getGrantedGem(), result.balanceAfter());
                }
                if (!payment.isApprovable()) {
                    throw new IllegalStateException("승인할 수 없는 결제입니다");
                }

                requested.set(true);
                KakaoPayApproval approval = kakaoPayClient.approve(payment.getPaymentKey(), orderId, memberId, pgToken);
                approved.set(new Approved(payment.getPaymentKey(), approval.totalAmount()));

                if (approval.totalAmount() != payment.getAmount()) {
                    throw new IllegalStateException("결제 금액이 일치하지 않습니다");
                }

                payment.markPaid(clock.instant());
                GemResult result = gemWallet.apply(purchaseCommand(payment));
                return new PaymentApproveResponse(orderId, payment.getGrantedGem(), result.balanceAfter());
            });
        } catch (RuntimeException e) {
            if (requested.get()) {
                compensateFailedApproval(orderId, approved.get());
            }
            throw e;
        }
    }

    /** 카카오페이에 승인을 요청한 뒤 실패: 돈만 빠지고 보석은 없는 상태가 남지 않게 정리한다 */
    private void compensateFailedApproval(String orderId, Approved approved) {
        String reason = "결제 승인 실패";
        if (approved != null) {
            reason = "승인 후 처리 실패로 자동 취소";
            try {
                kakaoPayClient.cancel(approved.tid(), approved.amount());
            } catch (RuntimeException cancelError) {
                log.error("승인 후 처리 실패, 카카오페이 결제 취소도 실패. 수동 확인 필요 orderId={} tid={}",
                        orderId, approved.tid(), cancelError);
            }
        }

        String failReason = reason;
        try {
            transactionTemplate.executeWithoutResult(status ->
                    paymentRepository.findByOrderIdForUpdate(orderId)
                            .filter(payment -> payment.getStatus() == PaymentStatus.READY)
                            .ifPresent(payment -> payment.markFailed(clock.instant(), failReason)));
        } catch (RuntimeException markError) {
            log.error("결제 실패 상태 저장 실패 orderId={}", orderId, markError);
        }
    }

    // ===== 결제창에서 취소·실패 =====

    /** 카카오페이 cancel_url, fail_url로 돌아왔을 때 프론트가 부른다. 대기 중인 결제만 실패로 바꾸고 나머지는 무시 */
    @Transactional
    public void abort(Long memberId, String orderId) {
        Payment payment = findOwnedForUpdate(memberId, orderId);
        if (payment.getStatus() == PaymentStatus.READY) {
            payment.markFailed(clock.instant(), "사용자가 결제를 취소했습니다");
        }
    }

    // ===== 환불 =====

    /** 결제 후 환불 기간 안이고, 지급한 보석을 그대로 회수할 수 있을 때만 환불한다 */
    public PaymentRefundResponse refund(Long memberId, String orderId) {
        AtomicBoolean canceledAtKakao = new AtomicBoolean(false);
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = findOwnedForUpdate(memberId, orderId);
                Instant now = clock.instant();
                if (!payment.isRefundable(now, refundWindow)) {
                    throw new IllegalStateException("환불할 수 없는 결제입니다 (결제 후 " + refundWindow.toDays() + "일 이내만 가능)");
                }

                GemResult result;
                try {
                    result = gemWallet.apply(GemCommand.purchaseRefund(memberId, payment.getId(), payment.getGrantedGem()));
                } catch (InsufficientGemException e) {
                    throw new IllegalStateException("구매한 보석을 이미 사용해서 환불할 수 없습니다");
                }

                kakaoPayClient.cancel(payment.getPaymentKey(), payment.getAmount());   // 실패하면 보석 회수도 롤백
                canceledAtKakao.set(true);

                payment.markRefunded(now, "사용자 환불 요청");
                return new PaymentRefundResponse(orderId, payment.getGrantedGem(), result.balanceAfter());
            });
        } catch (RuntimeException e) {
            if (canceledAtKakao.get()) {
                log.error("카카오페이 결제는 취소됐지만 환불 저장 실패. 수동 확인 필요 orderId={}", orderId, e);
            }
            throw e;
        }
    }

    // ===== 조회 =====

    /** 결제 완료·환불된 내역만. 결제창에서 그만둔 건은 보여주지 않는다 */
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getMyPayments(Long memberId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_PAGE_SIZE);
        List<Payment> payments = paymentRepository
                .findByMemberIdAndStatusInOrderByCreatedAtDescIdDesc(memberId, VISIBLE_STATUSES, PageRequest.of(safePage, safeSize))
                .getContent();

        Map<Long, String> productNames = productRepository
                .findAllById(payments.stream().map(Payment::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ShopProduct::getId, ShopProduct::getName));

        Instant now = clock.instant();
        return payments.stream()
                .map(payment -> PaymentHistoryResponse.of(
                        payment,
                        productNames.get(payment.getProductId()),
                        payment.isRefundable(now, refundWindow)))
                .toList();
    }

    // ===== 내부 =====

    /** 남의 주문이면 있다는 것도 알리지 않는다 */
    private Payment findOwnedForUpdate(Long memberId, String orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .filter(payment -> payment.isOwnedBy(memberId))
                .orElseThrow(() -> new NotFoundException("결제를 찾을 수 없습니다"));
    }

    private static GemCommand purchaseCommand(Payment payment) {
        return GemCommand.gemPurchase(payment.getMemberId(), payment.getId(), payment.getGrantedGem());
    }

    /** 카카오페이에서 승인된 결제. 우리 쪽 처리가 실패하면 이 값으로 취소한다 */
    private record Approved(String tid, int amount) {
    }
}
