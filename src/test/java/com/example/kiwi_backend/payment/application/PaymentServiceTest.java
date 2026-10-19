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
import com.example.kiwi_backend.payment.infra.toss.TossPaymentConfirmation;
import com.example.kiwi_backend.payment.infra.toss.TossPaymentsClient;
import com.example.kiwi_backend.payment.infra.toss.TossPaymentsProperties;
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
    private static final String PAYMENT_KEY = "pk-123";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ShopProductRepository productRepository = mock(ShopProductRepository.class);
    private final MemberQueryService memberQueryService = mock(MemberQueryService.class);
    private final GemWallet gemWallet = mock(GemWallet.class);
    private final TossPaymentsClient tossClient = mock(TossPaymentsClient.class);
    private final TossPaymentsProperties tossProperties = new TossPaymentsProperties(
            "https://api.tosspayments.com", "test_sk", "test_ck",
            "http://localhost:3000/shop/payment/{orderId}/success",
            "http://localhost:3000/shop/payment/{orderId}/fail");

    private final PaymentService service = new PaymentService(
            paymentRepository, productRepository, memberQueryService, gemWallet, tossClient, tossProperties,
            mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));

    // ===== 준비 =====

    @Test
    void readySavesPaymentAndReturnsCheckoutParams() {
        when(memberQueryService.getSummary(MEMBER_ID)).thenReturn(new MemberSummary(MEMBER_ID, Gender.MALE, true, true));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product()));
        when(paymentRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        PaymentReadyResponse response = service.ready(MEMBER_ID, 5L);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        String orderId = saved.getValue().getOrderId();
        assertThat(saved.getValue().getPaymentKey()).isNull();
        assertThat(response).isEqualTo(new PaymentReadyResponse(
                orderId, "보석 30개", 3000, 33, "test_ck",
                "http://localhost:3000/shop/payment/" + orderId + "/success",
                "http://localhost:3000/shop/payment/" + orderId + "/fail"));
        verifyNoInteractions(tossClient);
    }

    // ===== 승인 =====

    @Test
    void approveGrantsGemsAfterTossConfirm() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(tossClient.confirm(PAYMENT_KEY, ORDER_ID, 3000)).thenReturn(confirmation(3000));
        when(gemWallet.apply(any())).thenReturn(new GemResult(1L, 133, false));

        PaymentApproveResponse response = service.approve(MEMBER_ID, ORDER_ID, PAYMENT_KEY, 3000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(response).isEqualTo(new PaymentApproveResponse(ORDER_ID, 33, 133));
        ArgumentCaptor<GemCommand> command = ArgumentCaptor.forClass(GemCommand.class);
        verify(gemWallet).apply(command.capture());
        assertThat(command.getValue().type()).isEqualTo(GemTransactionType.GEM_PURCHASE);
        assertThat(command.getValue().amount()).isEqualTo(33);
        assertThat(command.getValue().paymentId()).isEqualTo(payment.getId());
        verify(tossClient, never()).cancel(anyString(), anyInt(), anyString());
    }

    @Test
    void approveAgainReturnsSameResultWithoutCallingToss() {
        Payment payment = paidPayment(NOW);
        givenLocked(payment);
        when(gemWallet.apply(any())).thenReturn(new GemResult(1L, 133, true));

        PaymentApproveResponse response = service.approve(MEMBER_ID, ORDER_ID, PAYMENT_KEY, 3000);

        assertThat(response.gemBalance()).isEqualTo(133);
        verifyNoInteractions(tossClient);
    }

    @Test
    void approveWithTamperedAmountIsRejectedBeforeConfirm() {
        Payment payment = readyPayment();
        givenLocked(payment);

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, PAYMENT_KEY, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("금액");

        verifyNoInteractions(tossClient, gemWallet);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void approveWithWrongConfirmedAmountCancelsAtTossAndFails() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(tossClient.confirm(PAYMENT_KEY, ORDER_ID, 3000)).thenReturn(confirmation(100));

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, PAYMENT_KEY, 3000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("금액");

        verify(tossClient).cancel(eq(PAYMENT_KEY), eq(100), anyString());
        verifyNoInteractions(gemWallet);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void approveCancelsAtTossWhenGemGrantFails() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(tossClient.confirm(PAYMENT_KEY, ORDER_ID, 3000)).thenReturn(confirmation(3000));
        when(gemWallet.apply(any())).thenThrow(new NotFoundException("지갑이 없습니다"));

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, PAYMENT_KEY, 3000)).isInstanceOf(NotFoundException.class);

        verify(tossClient).cancel(eq(PAYMENT_KEY), eq(3000), anyString());
    }

    @Test
    void approveRejectedByTossMarksFailedWithoutCancel() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(tossClient.confirm(PAYMENT_KEY, ORDER_ID, 3000))
                .thenThrow(new PaymentGatewayException("토스페이먼츠 결제 처리에 실패했습니다"));

        assertThatThrownBy(() -> service.approve(MEMBER_ID, ORDER_ID, PAYMENT_KEY, 3000)).isInstanceOf(PaymentGatewayException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getCancelReason()).isEqualTo("결제 승인 실패");
        verify(tossClient, never()).cancel(anyString(), anyInt(), anyString());
    }

    @Test
    void cannotApproveOrFailSomeoneElsesOrder() {
        Payment payment = readyPayment();
        givenLocked(payment);

        assertThatThrownBy(() -> service.approve(999L, ORDER_ID, PAYMENT_KEY, 3000)).isInstanceOf(NotFoundException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(tossClient, gemWallet);
    }

    // ===== 환불 =====

    @Test
    void refundRevokesGemsThenCancelsAtToss() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(1)));
        givenLocked(payment);
        when(gemWallet.apply(any())).thenReturn(new GemResult(2L, 0, false));

        PaymentRefundResponse response = service.refund(MEMBER_ID, ORDER_ID);

        assertThat(response).isEqualTo(new PaymentRefundResponse(ORDER_ID, 33, 0));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<GemCommand> command = ArgumentCaptor.forClass(GemCommand.class);
        verify(gemWallet).apply(command.capture());
        assertThat(command.getValue().type()).isEqualTo(GemTransactionType.PURCHASE_REFUND);
        assertThat(command.getValue().amount()).isEqualTo(33);
        verify(tossClient).cancel(PAYMENT_KEY, 3000, "사용자 환불 요청");
    }

    @Test
    void refundRejectedWhenGemsAlreadySpent() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(1)));
        givenLocked(payment);
        when(gemWallet.apply(any())).thenThrow(new InsufficientGemException(10, 33));

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 사용");

        verifyNoInteractions(tossClient);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundRejectedAfterWindow() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(8)));
        givenLocked(payment);

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환불 기간이 지났습니다");

        verifyNoInteractions(gemWallet, tossClient);
    }

    @Test
    void refundAgainSaysAlreadyRefunded() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(1)));
        payment.markRefunded(NOW, "사용자 환불 요청");
        givenLocked(payment);

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 환불된 결제입니다");

        verifyNoInteractions(gemWallet, tossClient);
    }

    @Test
    void refundUnpaidSaysNotCompleted() {
        givenLocked(readyPayment());

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제가 완료되지 않아 환불할 수 없습니다");
    }

    // ===== 결제창 취소 =====

    @Test
    void abortMarksOnlyReadyPaymentFailed() {
        Payment ready = readyPayment();
        givenLocked(ready);
        service.abort(MEMBER_ID, ORDER_ID);
        assertThat(ready.getStatus()).isEqualTo(PaymentStatus.FAILED);

        Payment paid = paidPayment(NOW);
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
        return withId(Payment.ready(MEMBER_ID, product(), ORDER_ID));
    }

    private static Payment paidPayment(Instant paidAt) {
        Payment payment = readyPayment();
        payment.markPaid(PAYMENT_KEY, paidAt);
        return payment;
    }

    private static Payment withId(Payment payment) {
        ReflectionTestUtils.setField(payment, "id", 100L);
        return payment;
    }

    private static TossPaymentConfirmation confirmation(int total) {
        return new TossPaymentConfirmation(PAYMENT_KEY, ORDER_ID, "DONE", total, "카드");
    }
}
