package com.example.kiwi_backend.vote.application;

import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.interaction.application.PostSessionWindowService;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.*;
import com.example.kiwi_backend.vote.domain.*;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PaidResultTransactionTest {
    @Autowired PaidResultService service;
    @Autowired VoteRepository votes;
    @Autowired ResultPurchaseRepository purchases;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired WalletService wallet;
    @Autowired PostSessionWindowService windows;
    private Long sessionId;
    private long buyer, first, chosen, other;

    @BeforeEach
    void setup() {
        Instant now = Instant.now();
        var session = DatingSession.create(now.minusSeconds(600), 3, 180, 120, 300, 12, 2, now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200)); session.markReady(); session.startProgress(); session.markRoundsStarted();
        session.startHearts(now.minusSeconds(181)); session.startVoting(now.minusSeconds(121)); session.finishVoting(now);
        sessionId = sessions.saveAndFlush(session).getId();
        buyer = 60000 + sessionId * 10; first = buyer + 1; chosen = buyer + 2; other = buyer + 3;
        for (long id : List.of(buyer, first, chosen, other)) {
            var p = SessionParticipant.apply(sessionId, id, id == buyer || id == other ? Gender.MALE : Gender.FEMALE, "alias-" + id, now);
            p.attend(); p.recordVote(now); participants.saveAndFlush(p); wallet.createWallet(id);
        }
        votes.saveAllAndFlush(List.of(Vote.create(sessionId, buyer, first, 1, now),
                Vote.create(sessionId, first, other, 1, now), Vote.create(sessionId, first, buyer, 2, now),
                Vote.create(sessionId, chosen, buyer, 1, now)));
        wallet.apply(GemCommand.adminGrant(buyer, 20, "test", UUID.randomUUID().toString()));
    }

    @Test
    void bundleCanTargetSomeoneOtherThanMyFirstChoiceAndRetriesAreFree() {
        var receipt = service.purchase(sessionId, buyer, ResultProduct.BUNDLE, chosen);
        assertThat(receipt.chargedGem()).isEqualTo(3);
        assertThat(receipt.data().myRank().rankOfMe()).isEqualTo(2);
        assertThat(receipt.data().rankings().getFirst().voter().memberId()).isEqualTo(chosen);
        assertThat(receipt.data().firstChoosers()).extracting(PaidResultService.Person::memberId).containsExactly(chosen);
        assertThat(service.purchase(sessionId, buyer, ResultProduct.BUNDLE, chosen).chargedGem()).isZero();
        assertThat(service.purchase(sessionId, buyer, ResultProduct.FIRST_CHOOSERS, null).chargedGem()).isZero();
        assertThat(wallet.getBalance(buyer).gemBalance()).isEqualTo(17);
        assertThat(service.get(sessionId, buyer, ResultProduct.USER_RANKINGS, chosen).rankings()).hasSize(1);
        assertThatThrownBy(() -> service.get(sessionId, buyer, ResultProduct.USER_RANKINGS, first))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unpaidAndClosedWindowsCannotReadResults() {
        assertThatThrownBy(() -> service.get(sessionId, buyer, ResultProduct.ALL_RANKINGS, null))
                .isInstanceOf(IllegalStateException.class);
        service.purchase(sessionId, buyer, ResultProduct.ALL_RANKINGS, null);
        assertThat(wallet.getBalance(buyer).gemBalance()).isEqualTo(10);
        assertThat(service.get(sessionId, buyer, ResultProduct.ALL_RANKINGS, null).rankings()).hasSize(4);
        windows.closeResults(sessionId, buyer);
        assertThatThrownBy(() -> service.get(sessionId, buyer, ResultProduct.ALL_RANKINGS, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.purchase(sessionId, buyer, ResultProduct.ALL_RANKINGS, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallet.getBalance(buyer).gemBalance()).isEqualTo(10);
    }

    @Test
    void insufficientBalanceDoesNotGrantAccess() {
        assertThatThrownBy(() -> service.purchase(sessionId, first, ResultProduct.ALL_RANKINGS, null))
                .isInstanceOf(RuntimeException.class);
        assertThat(purchases.findAllBySessionIdAndBuyerId(sessionId, first)).isEmpty();
        assertThatThrownBy(() -> service.get(sessionId, first, ResultProduct.ALL_RANKINGS, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOutsidersAndUnavailableFirstChoiceBeforeCharging() {
        assertThatThrownBy(() -> service.purchase(sessionId, buyer, ResultProduct.USER_RANKINGS, 999999L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purchase(sessionId, other, ResultProduct.MY_FIRST_CHOICE_RANK, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(purchases.findAllBySessionIdAndBuyerId(sessionId, other)).isEmpty();
        assertThat(wallet.getBalance(buyer).gemBalance()).isEqualTo(20);
    }
}
