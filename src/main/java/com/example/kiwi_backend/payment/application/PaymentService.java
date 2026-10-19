package com.example.kiwi_backend.payment.application;

import com.example.kiwi_backend.common.exception.InsufficientGemException;
import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import com.example.kiwi_backend.common.exception.PaymentResultUnknownException;
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
import com.example.kiwi_backend.payment.infra.toss.TossPaymentConfirmation;
import com.example.kiwi_backend.payment.infra.toss.TossPaymentsClient;
import com.example.kiwi_backend.payment.infra.toss.TossPaymentsProperties;
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
 * 토스페이먼츠로 보석 구매·환불.
 *
 * 동시성: 결제 행을 먼저 잠그고 토스 호출과 보석 변동을 같은 트랜잭션에서 한다.
 * 같은 주문에 승인 요청이 두 번 와도 두 번째는 첫 번째가 끝날 때까지 기다렸다가 PAID를 보고 그대로 돌아간다.
 * 잠금 순서는 항상 결제 → 지갑. 토스 응답을 기다리는 동안(최대 수 초) 이 잠금을 쥐고 있다.
 *
 * 금액: 결제창은 프론트가 금액을 넣어 열기 때문에 조작될 수 있다.
 * 승인 전에 successUrl로 받은 amount를 우리 DB 금액과 비교하고, 승인 응답의 totalAmount도 한 번 더 확인한다.
 *
 * 우리 DB와 토스가 어긋나는 경우:
 * - 승인: 토스 승인 후 우리 쪽 처리(금액 검증, 보석 지급, 커밋)가 실패하면 토스 결제를 바로 취소한다.
 *   그래서 승인 트랜잭션은 커밋 실패까지 잡을 수 있도록 @Transactional 대신 TransactionTemplate으로 감싼다.
 *   승인 응답을 못 받은 경우(시간 초과 등)는 토스에 결제를 조회해서, 승인돼 있으면 같은 방법으로 취소한다.
 *   조회도 실패하면 자동 복구하지 않고 "승인 결과 확인 실패"로 남긴 뒤 에러 로그를 남긴다.
 * - 환불: 보석 회수 → 토스 취소 순서. 토스 취소가 실패하면 보석 회수도 함께 롤백된다.
 *   단, 취소 요청이 실패해도 토스에 결제를 조회해서 이미 취소돼 있으면 환불을 그대로 완료한다
 *   (응답만 못 받은 경우, 그 뒤 다시 환불을 요청한 경우).
 *   취소 성공 뒤 커밋만 실패하는 경우는 자동 복구하지 않고 에러 로그를 남긴다.
 */
@Slf4j
@Service
public class PaymentService {

    private static final int MAX_HISTORY_PAGE_SIZE = 50;
    private static final String REFUND_REASON = "사용자 환불 요청";
    private static final List<PaymentStatus> VISIBLE_STATUSES = List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final ShopProductRepository productRepository;
    private final MemberQueryService memberQueryService;
    private final GemWallet gemWallet;
    private final TossPaymentsClient tossClient;
    private final TossPaymentsProperties tossProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration refundWindow;

    public PaymentService(PaymentRepository paymentRepository,
                          ShopProductRepository productRepository,
                          MemberQueryService memberQueryService,
                          GemWallet gemWallet,
                          TossPaymentsClient tossClient,
                          TossPaymentsProperties tossProperties,
                          PlatformTransactionManager transactionManager,
                          Clock clock,
                          @Value("${app.payment.refund-window:7d}") Duration refundWindow) {
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.memberQueryService = memberQueryService;
        this.gemWallet = gemWallet;
        this.tossClient = tossClient;
        this.tossProperties = tossProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.refundWindow = refundWindow;
    }

    // ===== 결제 준비 =====

    /** 결제 행을 만들고 프론트가 토스 결제창을 여는 데 필요한 값을 준다. 토스 호출은 없다 */
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

        return new PaymentReadyResponse(
                payment.getOrderId(),
                product.getName(),
                payment.getAmount(),
                payment.getGrantedGem(),
                tossProperties.clientKey(),
                tossProperties.successUrlFor(payment.getOrderId()),
                tossProperties.failUrlFor(payment.getOrderId())
        );
    }

    // ===== 결제 승인 =====

    /**
     * 사용자가 결제창에서 인증한 뒤 프론트가 successUrl의 paymentKey, amount를 넘겨 부른다.
     * 이미 승인된 주문이면 다시 지급하지 않고 같은 결과를 돌려준다 (새로고침, 중복 클릭).
     */
    public PaymentApproveResponse approve(Long memberId, String orderId, String paymentKey, int amount) {
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
                if (amount != payment.getAmount()) {   // 결제창 금액 조작. 승인 전이라 돈은 빠지지 않았다
                    throw new IllegalStateException("결제 금액이 일치하지 않습니다");
                }

                requested.set(true);
                TossPaymentConfirmation confirmation = tossClient.confirm(paymentKey, orderId, amount);
                approved.set(new Approved(confirmation.paymentKey(), confirmation.totalAmount()));

                if (confirmation.totalAmount() != payment.getAmount()) {
                    throw new IllegalStateException("결제 금액이 일치하지 않습니다");
                }

                payment.markPaid(confirmation.paymentKey(), clock.instant());
                GemResult result = gemWallet.apply(purchaseCommand(payment));
                return new PaymentApproveResponse(orderId, payment.getGrantedGem(), result.balanceAfter());
            });
        } catch (RuntimeException e) {
            if (requested.get()) {
                compensateFailedApproval(orderId, paymentKey, approved.get(), e);
            }
            throw e;
        }
    }

    /** 토스에 승인을 요청한 뒤 실패: 돈만 빠지고 보석은 없는 상태가 남지 않게 정리한다 */
    private void compensateFailedApproval(String orderId, String paymentKey, Approved approved, RuntimeException cause) {
        String reason = "결제 승인 실패";
        if (approved == null && cause instanceof PaymentResultUnknownException) {
            try {
                approved = findApproved(orderId, paymentKey);
            } catch (RuntimeException lookupError) {
                reason = "승인 결과 확인 실패";
                log.error("토스 승인 응답을 못 받았고 결제 조회도 실패. 수동 확인 필요 orderId={} paymentKey={}",
                        orderId, paymentKey, lookupError);
            }
        }
        if (approved != null) {
            reason = "승인 후 처리 실패로 자동 취소";
            try {
                tossClient.cancel(approved.paymentKey(), approved.amount(), reason);
            } catch (RuntimeException cancelError) {
                log.error("승인 후 처리 실패, 토스 결제 취소도 실패. 수동 확인 필요 orderId={} paymentKey={}",
                        orderId, approved.paymentKey(), cancelError);
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

    /**
     * 승인 응답을 못 받았을 때 토스에 실제 결과를 묻는다. 승인돼 있으면 취소할 값을, 아니면 null을 준다.
     * 아직 처리 중이라 판단할 수 없으면 예외를 던져 수동 확인 대상으로 남긴다.
     */
    private Approved findApproved(String orderId, String paymentKey) {
        TossPaymentConfirmation found = tossClient.find(paymentKey);
        if (!orderId.equals(found.orderId())) {
            throw new IllegalStateException("다른 주문의 결제입니다");
        }
        return switch (found.status()) {
            case "DONE" -> new Approved(found.paymentKey(), found.totalAmount());
            case "READY", "ABORTED", "EXPIRED", "CANCELED", "PARTIAL_CANCELED" -> null;
            default -> throw new IllegalStateException("결제 상태를 판단할 수 없습니다: " + found.status());
        };
    }

    // ===== 결제창에서 취소·실패 =====

    /** 토스 failUrl로 돌아왔을 때(사용자 취소 포함) 프론트가 부른다. 대기 중인 결제만 실패로 바꾸고 나머지는 무시 */
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
        AtomicBoolean canceledAtToss = new AtomicBoolean(false);
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = findOwnedForUpdate(memberId, orderId);
                Instant now = clock.instant();
                if (!payment.isRefundable(now, refundWindow)) {
                    throw new IllegalStateException(refundRejectReason(payment));
                }

                GemResult result;
                try {
                    result = gemWallet.apply(GemCommand.purchaseRefund(memberId, payment.getId(), payment.getGrantedGem()));
                } catch (InsufficientGemException e) {
                    throw new IllegalStateException("구매한 보석을 이미 사용해서 환불할 수 없습니다");
                }

                cancelForRefund(payment);   // 실패하면 보석 회수도 롤백
                canceledAtToss.set(true);

                payment.markRefunded(now, REFUND_REASON);
                return new PaymentRefundResponse(orderId, payment.getGrantedGem(), result.balanceAfter());
            });
        } catch (RuntimeException e) {
            if (canceledAtToss.get()) {
                log.error("토스 결제는 취소됐지만 환불 저장 실패. 수동 확인 필요 orderId={}", orderId, e);
            }
            throw e;
        }
    }

    /** 토스 결제를 취소한다. 취소 요청이 실패하면 토스에 조회해서 이미 취소돼 있을 때만 성공으로 본다 */
    private void cancelForRefund(Payment payment) {
        try {
            tossClient.cancel(payment.getPaymentKey(), payment.getAmount(), REFUND_REASON);
        } catch (PaymentGatewayException e) {
            if (!isCanceledAtToss(payment, e)) {
                throw e;
            }
            log.warn("토스 취소 요청은 실패했지만 조회해 보니 이미 취소됨. 환불 완료 처리 orderId={}", payment.getOrderId());
        }
    }

    private boolean isCanceledAtToss(Payment payment, PaymentGatewayException cancelError) {
        String tossStatus;
        try {
            tossStatus = tossClient.find(payment.getPaymentKey()).status();
        } catch (RuntimeException lookupError) {
            if (cancelError instanceof PaymentResultUnknownException) {
                log.error("토스 취소 응답을 못 받았고 결제 조회도 실패. 수동 확인 필요 orderId={} paymentKey={}",
                        payment.getOrderId(), payment.getPaymentKey(), lookupError);
            }
            return false;
        }
        return switch (tossStatus) {
            case "CANCELED" -> true;
            case "DONE" -> false;
            default -> {
                log.error("환불 중 토스 결제 상태를 판단할 수 없음. 수동 확인 필요 orderId={} status={}",
                        payment.getOrderId(), tossStatus);
                yield false;
            }
        };
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

    /** 환불 거절 사유. 사용자에게 그대로 보이므로 상태별로 나눠서 알려준다 */
    private String refundRejectReason(Payment payment) {
        return switch (payment.getStatus()) {
            case REFUNDED -> "이미 환불된 결제입니다";
            case PAID -> "환불 기간이 지났습니다 (결제 후 " + refundWindow.toDays() + "일 이내만 가능)";
            case READY, FAILED -> "결제가 완료되지 않아 환불할 수 없습니다";
        };
    }

    private static GemCommand purchaseCommand(Payment payment) {
        return GemCommand.gemPurchase(payment.getMemberId(), payment.getId(), payment.getGrantedGem());
    }

    /** 토스에서 승인된 결제. 우리 쪽 처리가 실패하면 이 값으로 취소한다 */
    private record Approved(String paymentKey, int amount) {
    }
}
