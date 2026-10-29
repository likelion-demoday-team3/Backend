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
import com.example.kiwi_backend.payment.api.dto.NicePayReturnRequest;
import com.example.kiwi_backend.payment.api.dto.PaymentApproveResponse;
import com.example.kiwi_backend.payment.api.dto.PaymentReadyResponse;
import com.example.kiwi_backend.payment.api.dto.PaymentRefundResponse;
import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.entity.ShopProduct;
import com.example.kiwi_backend.payment.domain.enumerated.PaymentStatus;
import com.example.kiwi_backend.payment.domain.repository.PaymentRepository;
import com.example.kiwi_backend.payment.domain.repository.ShopProductRepository;
import com.example.kiwi_backend.payment.infra.nicepay.NicePayApproval;
import com.example.kiwi_backend.payment.infra.nicepay.NicePayCheckout;
import com.example.kiwi_backend.payment.infra.nicepay.NicePayClient;
import org.junit.jupiter.api.BeforeEach;
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
    private final NicePayClient nicePayClient = mock(NicePayClient.class);

    private final PaymentService service = new PaymentService(
            paymentRepository, productRepository, memberQueryService, gemWallet, nicePayClient,
            mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));

    @BeforeEach
    void validSignatureByDefault() {
        when(nicePayClient.verifyAuthSignature("auth-token", "3000", "sig")).thenReturn(true);
    }

    // ===== 준비 =====

    @Test
    void readySavesPaymentWithoutTidAndReturnsCheckoutValues() {
        when(memberQueryService.getSummary(MEMBER_ID)).thenReturn(new MemberSummary(MEMBER_ID, Gender.MALE, true, true));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product()));
        when(paymentRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(nicePayClient.checkout()).thenReturn(new NicePayCheckout("client-1", "https://api/return"));

        PaymentReadyResponse response = service.ready(MEMBER_ID, 5L);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentKey()).isNull();
        assertThat(response).isEqualTo(new PaymentReadyResponse(
                saved.getValue().getOrderId(), "보석 30개", 3000, 33, "client-1", "https://api/return"));
    }

    @Test
    void readyWithoutNicePaySettingsSavesNothing() {
        when(memberQueryService.getSummary(MEMBER_ID)).thenReturn(new MemberSummary(MEMBER_ID, Gender.MALE, true, true));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product()));
        when(nicePayClient.checkout()).thenThrow(new PaymentGatewayException("결제 설정이 되어 있지 않습니다"));

        assertThatThrownBy(() -> service.ready(MEMBER_ID, 5L)).isInstanceOf(PaymentGatewayException.class);

        verify(paymentRepository, never()).save(any());
    }

    // ===== 승인 =====

    @Test
    void approveGrantsGemsAfterNicePayApproval() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(nicePayClient.approve(TID, 3000)).thenReturn(approval(3000));
        when(gemWallet.apply(any())).thenReturn(new GemResult(1L, 133, false));

        PaymentApproveResponse response = service.approve(returned("3000"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey()).isEqualTo(TID);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(response).isEqualTo(new PaymentApproveResponse(ORDER_ID, 33, 133));
        ArgumentCaptor<GemCommand> command = ArgumentCaptor.forClass(GemCommand.class);
        verify(gemWallet).apply(command.capture());
        assertThat(command.getValue().type()).isEqualTo(GemTransactionType.GEM_PURCHASE);
        assertThat(command.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(command.getValue().amount()).isEqualTo(33);
        assertThat(command.getValue().paymentId()).isEqualTo(payment.getId());
        verify(nicePayClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void approveAgainReturnsSameResultWithoutCallingNicePay() {
        Payment payment = paidPayment(NOW);
        givenLocked(payment);
        when(gemWallet.apply(any())).thenReturn(new GemResult(1L, 133, true));

        PaymentApproveResponse response = service.approve(returned("3000"));

        assertThat(response.gemBalance()).isEqualTo(133);
        verify(nicePayClient, never()).approve(anyString(), anyInt());
    }

    @Test
    void approveWithForgedSignatureChangesNothing() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(nicePayClient.verifyAuthSignature("auth-token", "3000", "sig")).thenReturn(false);

        assertThatThrownBy(() -> service.approve(returned("3000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 인증 정보를 확인할 수 없습니다");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(nicePayClient, never()).approve(anyString(), anyInt());
        verify(nicePayClient, never()).cancel(anyString(), anyString(), anyString());
        verifyNoInteractions(gemWallet);
    }

    @Test
    void approveWithTamperedAmountIsRejectedBeforeCallingNicePay() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(nicePayClient.verifyAuthSignature("auth-token", "100", "sig")).thenReturn(true);

        assertThatThrownBy(() -> service.approve(returned("100"))).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(nicePayClient, never()).approve(anyString(), anyInt());
    }

    @Test
    void approveWithWrongApprovedAmountCancelsAtNicePayAndFails() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(nicePayClient.approve(TID, 3000)).thenReturn(approval(100));

        assertThatThrownBy(() -> service.approve(returned("3000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("금액");

        verify(nicePayClient).cancel(TID, ORDER_ID, "승인 후 처리 실패로 자동 취소");
        verifyNoInteractions(gemWallet);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void approveCancelsAtNicePayWhenGemGrantFails() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(nicePayClient.approve(TID, 3000)).thenReturn(approval(3000));
        when(gemWallet.apply(any())).thenThrow(new NotFoundException("지갑이 없습니다"));

        assertThatThrownBy(() -> service.approve(returned("3000"))).isInstanceOf(NotFoundException.class);

        verify(nicePayClient).cancel(TID, ORDER_ID, "승인 후 처리 실패로 자동 취소");
    }

    @Test
    void approveRejectedByNicePayMarksFailedWithoutCancel() {
        Payment payment = readyPayment();
        givenLocked(payment);
        when(nicePayClient.approve(TID, 3000)).thenThrow(new PaymentGatewayException("나이스페이 결제 처리에 실패했습니다"));

        assertThatThrownBy(() -> service.approve(returned("3000"))).isInstanceOf(PaymentGatewayException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getCancelReason()).isEqualTo("결제 승인 실패");
        verify(nicePayClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void approveUnknownOrderFails() {
        when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(returned("3000"))).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(gemWallet);
    }

    @Test
    void failedAuthenticationMarksOnlyReadyPaymentFailed() {
        Payment ready = readyPayment();
        givenLocked(ready);
        service.failAuthentication(ORDER_ID, "사용자 취소");
        assertThat(ready.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(ready.getCancelReason()).isEqualTo("사용자 취소");

        Payment paid = paidPayment(NOW);
        givenLocked(paid);
        service.failAuthentication(ORDER_ID, "사용자 취소");
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ===== 환불 =====

    @Test
    void refundRevokesGemsThenCancelsAtNicePay() {
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
        verify(nicePayClient).cancel(TID, ORDER_ID, "사용자 환불 요청");
    }

    @Test
    void refundRejectedWhenGemsAlreadySpent() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(1)));
        givenLocked(payment);
        when(gemWallet.apply(any())).thenThrow(new InsufficientGemException(10, 33));

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 사용");

        verify(nicePayClient, never()).cancel(anyString(), anyString(), anyString());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundRejectedAfterWindow() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(8)));
        givenLocked(payment);

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환불 기간이 지났습니다");

        verifyNoInteractions(gemWallet);
        verify(nicePayClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void refundAgainSaysAlreadyRefunded() {
        Payment payment = paidPayment(NOW.minus(Duration.ofDays(1)));
        payment.markRefunded(NOW, "사용자 환불 요청");
        givenLocked(payment);

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 환불된 결제입니다");

        verifyNoInteractions(gemWallet);
    }

    @Test
    void refundUnpaidSaysNotCompleted() {
        givenLocked(readyPayment());

        assertThatThrownBy(() -> service.refund(MEMBER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제가 완료되지 않아 환불할 수 없습니다");
    }

    @Test
    void cannotRefundOrAbortSomeoneElsesOrder() {
        Payment payment = paidPayment(NOW);
        givenLocked(payment);

        assertThatThrownBy(() -> service.refund(999L, ORDER_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.abort(999L, ORDER_ID)).isInstanceOf(NotFoundException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(gemWallet);
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

    private static NicePayReturnRequest returned(String amount) {
        return new NicePayReturnRequest("0000", "인증 성공", TID, "client-1", ORDER_ID, amount, "auth-token", "sig");
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
        payment.attachPaymentKey(TID);
        payment.markPaid(paidAt);
        return payment;
    }

    private static Payment withId(Payment payment) {
        ReflectionTestUtils.setField(payment, "id", 100L);
        return payment;
    }

    private static NicePayApproval approval(int amount) {
        return new NicePayApproval(TID, ORDER_ID, amount, "card");
    }
}
