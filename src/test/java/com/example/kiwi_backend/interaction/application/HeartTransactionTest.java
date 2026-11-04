package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.interaction.domain.HeartRepository;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.*;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HeartTransactionTest {
    @Autowired HeartService service;
    @Autowired HeartRepository hearts;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired WalletService wallet;
    @Autowired LiveRoundRepository rounds;
    @Autowired LiveRoomRepository rooms;

    @Test
    void concurrentHeartsUseOnlyOneFreeAllowanceAndRollbackInsufficientDebit() throws Exception {
        Instant now = Instant.now();
        var session = DatingSession.create(now.plusSeconds(3600), 3, 180, 120, 300, 12, 2, now);
        session.openRecruiting(now);
        session.markReady();
        session.startProgress();
        session.markRoundsStarted();
        session.startHearts(now);
        Long sessionId = sessions.saveAndFlush(session).getId();
        Long sender = 90001L;
        for (long id = 90001; id <= 90004; id++) {
            var participant = SessionParticipant.apply(sessionId, id,
                    id == 90001 ? Gender.MALE : Gender.FEMALE, "alias-" + id, now);
            participant.attend();
            participants.saveAndFlush(participant);
        }
        // 하트는 대화한 상대에게만 보낼 수 있으므로 세 상대와 각각 한 라운드씩 대화한 기록을 만든다
        for (int roundNumber = 1; roundNumber <= 3; roundNumber++) {
            var round = LiveRound.create(sessionId, roundNumber);
            round.start(now.minusSeconds(600L - roundNumber * 180L));
            round.complete(now.minusSeconds(420L - roundNumber * 180L));
            rooms.saveAndFlush(LiveRoom.create(rounds.saveAndFlush(round), 1, sender, 90001L + roundNumber));
        }
        wallet.createWallet(sender);
        wallet.apply(GemCommand.adminGrant(sender, 1, "test", UUID.randomUUID().toString()));
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { gate.await(); return service.send(sessionId, sender, 90002L); });
            var second = executor.submit(() -> { gate.await(); return service.send(sessionId, sender, 90003L); });
            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).chargedGem() + second.get(10, TimeUnit.SECONDS).chargedGem())
                    .isEqualTo(1);
        }
        assertThat(wallet.getBalance(sender).gemBalance()).isZero();
        assertThat(service.send(sessionId, sender, 90002L).alreadySent()).isTrue();
        assertThatThrownBy(() -> service.send(sessionId, sender, 90004L)).isInstanceOf(RuntimeException.class);
        assertThat(hearts.findAllBySessionIdAndSenderId(sessionId, sender)).hasSize(2);
        assertThat(wallet.getBalance(sender).gemBalance()).isZero();
    }
}
