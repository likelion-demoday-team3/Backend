package com.example.kiwi_backend.payment.application;

import com.example.kiwi_backend.common.exception.InsufficientGemException;
import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.GemResult;
import com.example.kiwi_backend.contract.dto.GemTransactionType;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.MemberSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.payment.api.dto.PaymentApproveResponse;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    private static final Long MEMBER_ID = 7L;
    private static final String ORDER_ID = "order-1";
    private static final String TID = "T123";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ShopProductRepository productRepository = mock(ShopProductRepository.class);
    private final MemberQueryService memberQueryService = mock(MemberQueryService.class);
    private final GemWallet gemWallet = mock(GemWallet.class);
    private final KakaoPayClient kakaoPayClient = mock(KakaoPayClient.class);

    private final PaymentService service = new PaymentService(
            paymentRepository, productRepository, memberQueryService, gemWallet, kakaoPayClient,
            mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));

    // ===== 준비 =====

    @Test
    void readySavesPaymentWithTidAndReturnsRedirectUrls() {
        when(memberQueryService.getSummary(MEMBER_ID)).thenReturn(new MemberSummary(MEMBER_ID, Gender.MALE, true));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product()));
        when(paymentRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(kakaoPayClient.ready(anyString(), eq(MEMBER_ID), eq("보석 30개"), eq(3000)))
                .thenReturn(new KakaoPayReady(TID, "https://pc", "https://m", "https://app"));

        PaymentReadyResponse response = service.ready(MEMBER_ID, 5L);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentKey()).isEqualTo(TID);
        assertThat(response.orderId()).isEqualTo(saved.getValue().getOrderId());
        assertThat(response.gemAmount()).isEqualTo(33);
        assertThat(response.pcRedirectUrl()).isEqualTo("https://pc");
    }

    // ===== 승인 =====

    @Test
    void approveGrantsGemsAfterKakaoApproval() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(kakaoPayClient.approve(TID, ORDER_ID, MEMBER_ID, "pg")).thenReturn(approval(3000));
        when(gemWallet.apply(any())).thenReturn(new GemResult(1L, 133, false));

        PaymentApproveResponse response = service.approve(MEMBER_ID, ORDER_ID, "pg");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(response).isEqualTo(new PaymentApproveResponse(ORDER_ID, 33, 133));
        ArgumentCaptor<GemCommand> command = ArgumentCaptor.forClass(GemCommand.class);
        verify(gemWallet).apply(command.capture());
        assertThat(command.getValue().type()).isEqualTo(GemTransactionType.GEM_PURCHASE);
        assertThat(command.getValue().amount()).isEqualTo(33);
        assertThat(command.getValue().paymentId()).isEqualTo(payment.getId());
        verify(kakaoPayClient, never()).cancel(anyString(), anyInt());
    }

    @Test
    void approveAgainReturnsSameResultWithoutCallingKakao() {
        Payment payment = readyPayment();
        payment.markPaid(NOW);
        givenLocked(payment);
        when(gemWallet.apply(any())).thenReturn(new GemResult(1L, 133, true));

        PaymentApproveResponse response = service.approve(MEMBER_ID, ORDER_ID, "pg");

        assertThat(response.gemBalance()).isEqualTo(133);
        verifyNoInteractions(kakaoPayClient);
    }

    @Test
    void approveWithWrongAmountCancelsAtKakaoAndFails() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(kakaoPayClient.approve(TID, ORDER_ID, MEMBER_ID, "pg")).thenReturn(approval(100));

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, "pg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("금액");

        verify(kakaoPayClient).cancel(TID, 100);
        verifyNoInteractions(gemWallet);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void approveCancelsAtKakaoWhenGemGrantFails() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(kakaoPayClient.approve(TID, ORDER_ID, MEMBER_ID, "pg")).thenReturn(approval(3000));
        when(gemWallet.apply(any())).thenThrow(new NotFoundException("지갑이 없습니다"));

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, "pg")).isInstanceOf(NotFoundException.class);

        verify(kakaoPayClient).cancel(TID, 3000);
    }

    @Test
    void approveRejectedByKakaoMarksFailedWithoutCancel() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(kakaoPayClient.approve(TID, ORDER_ID, MEMBER_ID, "pg"))
                .thenThrow(new PaymentGatewayException("카카오페이 결제 처리에 실패했습니다"));

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, "pg")).isInstanceOf(PaymentGatewayException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getCancelReason()).isEqualTo("결제 승인 실패");
        verify(kakaoPayClient, never()).cancel(anyString(), anyInt());
    }

    @Test
    void cannotApproveOrFailSomeoneElsesOrder() {
        Payment payment = readyPayment();
        givenLocked(payment);

        assertThatThrownBy(() -> service.approve(999L, ORDER_ID, "pg")).isInstanceOf(NotFoundException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(kakaoPayClient, gemWallet);
    }

    // ===== 환불 =====

    @Test
    void refundRevokesGemsThenCancelsAtKakao() {
        Payment payment = readyPayment();
        payment.markPaid(NOW.minus(Duration.ofDays(1)));
        givenLocked(payment);
        when(gemWallet.apply(any())).thenReturn(new GemResult(2L, 0, false));

        PaymentRefundResponse response = service.refund(MEMBER_ID, ORDER_ID);

        assertThat(response).isEqualTo(new PaymentRefundResponse(ORDER_ID, 33, 0));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<GemCommand> command = ArgumentCaptor.forClass(GemCommand.class);
        verify(gemWallet).apply(command.capture());
        assertThat(command.getValue().type()).isEqualTo(GemTransactionType.PURCHASE_REFUND);
        assertThat(command.getValue().amount()).isEqualTo(33);
        verify(kakaoPayClient).cancel(TID, 3000);
    }

    @Test
    void refundRejectedWhenGemsAlreadySpent() {
        Payment payment = readyPayment();
        payment.markPaid(NOW.minus(Duration.ofDays(1)));
        givenLocked(payment);
        when(gemWallet.apply(any())).thenThrow(new InsufficientGemException(10, 33));

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 사용");

        verifyNoInteractions(kakaoPayClient);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundRejectedAfterWindow() {
        Payment payment = readyPayment();
        payment.markPaid(NOW.minus(Duration.ofDays(8)));
        givenLocked(payment);

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(gemWallet, kakaoPayClient);
    }

    // ===== 결제창 취소 =====

    @Test
    void abortMarksOnlyReadyPaymentFailed() {
        Payment ready = readyPayment();
        givenLocked(ready);
        service.abort(MEMBER_ID, ORDER_ID);
        assertThat(ready.getStatus()).isEqualTo(PaymentStatus.FAILED);

        Payment paid = readyPayment();
        paid.markPaid(NOW);
        givenLocked(paid);
        service.abort(MEMBER_ID, ORDER_ID);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ===== 픽스처 =====

    private void givenLocked(Payment payment) {
        when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
    }

    private static ShopProduct product() {
        ShopProduct product = ShopProduct.create("보석 30개", 30, 3, 3000);
        ReflectionTestUtils.setField(product, "id", 5L);
        return product;
    }

    private static Payment readyPayment() {
        Payment payment = withId(Payment.ready(MEMBER_ID, product(), ORDER_ID));
        payment.attachPaymentKey(TID);
        return payment;
    }

    private static Payment withId(Payment payment) {
        ReflectionTestUtils.setField(payment, "id", 100L);
        return payment;
    }

    private static KakaoPayApproval approval(int total) {
        return new KakaoPayApproval("A1", TID, total, "MONEY");
    }
}
