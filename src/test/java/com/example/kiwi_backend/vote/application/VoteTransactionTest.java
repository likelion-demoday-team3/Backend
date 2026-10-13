package com.example.kiwi_backend.vote.application;

import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.*;
import com.example.kiwi_backend.vote.domain.*;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class VoteTransactionTest {
    @Autowired VoteService service;
    @Autowired VoteRepository votes;
    @Autowired MatchResultRepository matches;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired WalletService wallet;
    @MockitoBean Clock clock;
    private final Instant start = Instant.parse("2030-01-01T12:00:00Z");
    private final AtomicReference<Instant> now = new AtomicReference<>(start);
    private Long sessionId;
    private final long sender = 80001L;
    private final long receiver = 80002L;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenAnswer(invocation -> now.get());
        var session = DatingSession.create(start.minusSeconds(120), 3, 180, 120, 300, 12, 2, start.minusSeconds(7200));
        session.openRecruiting(start.minusSeconds(7200));
        session.markReady(); session.startProgress(); session.markRoundsStarted();
        session.startHearts(start.minusSeconds(60)); session.startVoting(start);
        sessionId = sessions.saveAndFlush(session).getId();
        for (long id = 80001; id <= 80004; id++) {
            var p = SessionParticipant.apply(sessionId, id, id % 2 == 1 ? Gender.MALE : Gender.FEMALE, "alias-" + id, start);
            p.attend(); participants.saveAndFlush(p); wallet.createWallet(id);
        }
    }

    @Test
    void emptySubmissionIsRecordedAndRewardedOnlyOnce() {
        int before = wallet.getBalance(sender).gemBalance();
        service.submit(sessionId, sender, List.of());
        service.submit(sessionId, sender, List.of());
        assertThat(service.ownVote(sessionId, sender).submitted()).isTrue();
        assertThat(wallet.getBalance(sender).gemBalance()).isEqualTo(before + 2);
        assertThat(participants.findBySessionIdAndMemberId(sessionId, sender).orElseThrow().getVotedAt()).isEqualTo(start);
        assertThatThrownBy(() -> service.submit(sessionId, sender, List.of(receiver))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deadlineRejectsLateVoteAndCloseMatchesOnlyMutualFirstChoicesOnce() {
        service.submit(sessionId, sender, List.of(receiver, 80004L));
        service.submit(sessionId, receiver, List.of(sender, 80003L));
        service.submit(sessionId, 80003L, List.of(receiver));
        now.set(start.plusSeconds(120));
        assertThatThrownBy(() -> service.submit(sessionId, 80004L, List.of(sender))).isInstanceOf(IllegalStateException.class);
        service.close(sessionId); service.close(sessionId);
        assertThat(service.summary(sessionId, sender).coupleCount()).isEqualTo(1);
        assertThat(matches.findAllBySessionId(sessionId)).hasSize(1);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void validatesTargetsAndDoesNotCloseEarly() {
        assertThatThrownBy(() -> service.submit(sessionId, sender, List.of(receiver, receiver)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(sessionId, sender, List.of(80003L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(sessionId, sender, List.of(99999L)))
                .isInstanceOf(IllegalArgumentException.class);
        service.close(sessionId);
        assertThat(sessions.findById(sessionId).orElseThrow().getLiveStatus()).isEqualTo(LiveStatus.VOTING);
        now.set(start.plusSeconds(120)); service.close(sessionId);
        assertThat(service.summary(sessionId, sender).coupleCount()).isZero();
    }
}
