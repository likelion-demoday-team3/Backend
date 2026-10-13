package com.example.kiwi_backend.payment.domain;

import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.entity.ShopProduct;
import com.example.kiwi_backend.payment.domain.enumerated.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    private static final Duration WINDOW = Duration.ofDays(7);

    @Test
    void readySnapshotsPriceAndTotalGemOfProduct() {
        Payment payment = Payment.ready(1L, product(30, 3, 3000), "order-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getAmount()).isEqualTo(3000);
        assertThat(payment.getGrantedGem()).isEqualTo(33);
        assertThat(payment.getProductId()).isEqualTo(5L);
        assertThat(payment.isApprovable()).isFalse();   // tid 받기 전

        payment.attachPaymentKey("T1");
        assertThat(payment.isApprovable()).isTrue();
    }

    @Test
    void paidPaymentIsRefundableOnlyWithinWindow() {
        Payment payment = Payment.ready(1L, product(10, 0, 1000), "order-1");
        payment.attachPaymentKey("T1");
        payment.markPaid(NOW);

        assertThat(payment.isRefundable(NOW.plus(WINDOW).minusSeconds(1), WINDOW)).isTrue();
        assertThat(payment.isRefundable(NOW.plus(WINDOW), WINDOW)).isFalse();
    }

    @Test
    void cannotPayTwiceOrRefundUnpaid() {
        Payment payment = Payment.ready(1L, product(10, 0, 1000), "order-1");
        payment.attachPaymentKey("T1");

        assertThatThrownBy(() -> payment.markRefunded(NOW, "환불")).isInstanceOf(IllegalStateException.class);

        payment.markPaid(NOW);
        assertThatThrownBy(() -> payment.markPaid(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.markFailed(NOW, "실패")).isInstanceOf(IllegalStateException.class);

        payment.markRefunded(NOW, "환불");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.isRefundable(NOW, WINDOW)).isFalse();
    }

    private static ShopProduct product(int gem, int bonus, int price) {
        ShopProduct product = ShopProduct.create("보석", gem, bonus, price);
        ReflectionTestUtils.setField(product, "id", 5L);
        return product;
    }
}
