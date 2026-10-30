package com.example.kiwi_backend.common;

import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.api.dto.MyInfoResponse;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.entity.ShopProduct;
import com.example.kiwi_backend.payment.domain.repository.PaymentRepository;
import com.example.kiwi_backend.payment.domain.repository.ShopProductRepository;
import com.example.kiwi_backend.session.application.service.SessionReadService;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.LiveStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import com.example.kiwi_backend.wallet.api.dto.WalletHistoryResponse;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 프론트가 읽는데 빠져 있던 응답 필드가 실제로 담기는지 */
@SpringBootTest
@ActiveProfiles("test")
class FrontendResponseFieldsTest {

    @Autowired SessionReadService sessionReads;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired ShopProductRepository products;
    @Autowired PaymentRepository payments;
    @Autowired WalletService wallet;

    /** 투표 중 새로고침해도 프론트가 투표 화면과 남은 시간을 되살릴 수 있어야 한다 */
    @Test
    void sessionDetailCarriesLiveStateForReconnect() {
        Instant now = Instant.now();
        var session = DatingSession.create(now.minusSeconds(600), 3, 180, 120, 300, 0, 0, now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        session.markReady(); session.startProgress(); session.markRoundsStarted();
        session.startHearts(now.minusSeconds(61));
        Instant votingStarted = now.minusSeconds(1);
        session.startVoting(votingStarted);
        Long sessionId = sessions.saveAndFlush(session).getId();
        long memberId = 120_001L + sessionId;
        var me = SessionParticipant.apply(sessionId, memberId, Gender.MALE, "alias", now.minusSeconds(7000));
        me.attend();
        participants.saveAndFlush(me);

        var detail = sessionReads.detail(sessionId, memberId);

        assertThat(detail.liveStatus()).isEqualTo(LiveStatus.VOTING);
        // DB는 마이크로초까지만 저장하므로 1ms 오차는 허용한다
        assertThat(detail.phaseEndsAt()).isCloseTo(votingStarted.plusSeconds(120), within(1, ChronoUnit.MILLIS));
        assertThat(detail.currentRoundNumber()).isEqualTo(session.getCurrentRoundNumber());
    }

    @Test
    void myInfoCarriesIdentityVerifiedAt() {
        Member member = Member.register("verified@yonsei.ac.kr", "테스트", "hash", "연세대학교", Gender.FEMALE,
                LocalDate.of(2003, 1, 1), null, "kiwi_test", Instant.parse("2026-10-30T00:00:00Z"),
                LocalDate.of(2026, 10, 30));
        assertThat(MyInfoResponse.from(member).identityVerifiedAt()).isNull();

        Instant verifiedAt = Instant.parse("2026-10-31T01:00:00Z");
        member.beginIdentityVerification("identity-1");
        member.verifyIdentity(verifiedAt, "identity-1");
        assertThat(MyInfoResponse.from(member).identityVerifiedAt()).isEqualTo(verifiedAt);
    }

    /** 충전 내역 상세가 시간 추측 없이 정확한 결제로 연결되도록 주문 번호와 결제 금액을 함께 준다 */
    @Test
    void walletHistoryCarriesOrderIdAndPaymentAmountForPurchases() {
        long memberId = 130_001L + System.nanoTime() % 1_000_000;
        wallet.createWallet(memberId);
        ShopProduct product = products.saveAndFlush(ShopProduct.create("보석 30개", 30, 0, 3000));
        String orderId = UUID.randomUUID().toString();
        Payment payment = payments.saveAndFlush(Payment.ready(memberId, product, orderId));
        wallet.apply(GemCommand.gemPurchase(memberId, payment.getId(), 30));
        wallet.apply(GemCommand.adminGrant(memberId, 5, "test", UUID.randomUUID().toString()));

        var history = wallet.getHistory(memberId, 0, 20);

        WalletHistoryResponse purchase = history.stream()
                .filter(item -> item.orderId() != null).findFirst().orElseThrow();
        assertThat(purchase.orderId()).isEqualTo(orderId);
        assertThat(purchase.paymentAmount()).isEqualTo(3000);
        assertThat(history).filteredOn(item -> item.orderId() == null).singleElement()
                .satisfies(item -> assertThat(item.paymentAmount()).isNull());
    }
}
