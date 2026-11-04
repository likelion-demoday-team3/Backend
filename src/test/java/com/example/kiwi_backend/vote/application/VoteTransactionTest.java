package com.example.kiwi_backend.vote.application;

import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.notification.application.NotificationService;
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
    @Autowired MatchHistoryService history;
    @Autowired VoteRepository votes;
    @Autowired MatchResultRepository matches;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired WalletService wallet;
    @Autowired NotificationService notifications;
    @Autowired LiveRoundRepository rounds;
    @Autowired LiveRoomRepository rooms;
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
        // 투표는 대화한 상대만 고를 수 있다. 남 80001·80003, 여 80002·80004가 두 라운드에 걸쳐 서로 한 번씩 대화했다
        meet(1, true, new long[][]{{80001L, 80002L}, {80003L, 80004L}});
        meet(2, true, new long[][]{{80001L, 80004L}, {80003L, 80002L}});
    }

    /** 라운드와 방을 만든다. started가 false면 아직 시작하지 않은(PENDING) 라운드 */
    private void meet(int roundNumber, boolean started, long[][] pairs) {
        var round = LiveRound.create(sessionId, roundNumber);
        if (started) {
            round.start(start.minusSeconds(900L - roundNumber * 200L));
            round.complete(start.minusSeconds(720L - roundNumber * 200L));
        }
        var saved = rounds.saveAndFlush(round);
        for (int table = 0; table < pairs.length; table++) {
            rooms.saveAndFlush(LiveRoom.create(saved, table + 1, pairs[table][0], pairs[table][1]));
        }
    }

    @Test
    void emptySubmissionIsRecordedAndRewardedOnlyOnce() {
        int before = wallet.getBalance(sender).gemBalance();
        service.submit(sessionId, sender, List.of());
        service.submit(sessionId, sender, List.of());
        assertThat(service.ownVote(sessionId, sender).submitted()).isTrue();
        assertThat(wallet.getBalance(sender).gemBalance()).isEqualTo(before + 2);
        // 두 번 제출해도 보증금 알림은 한 번
        assertThat(notifications.list(sender, 0, 50).items()).filteredOn(item -> sessionId.equals(item.sessionId()))
                .singleElement().satisfies(item -> {
                    assertThat(item.type()).isEqualTo(NotificationType.COMPLETION_REFUND);
                    assertThat(item.body()).isEqualTo("투표를 완료해 보석 ◆2개를 돌려드렸어요.");
                    assertThat(item.link()).isNull();
                });
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
        // 마이페이지 매칭 기록: 성공한 사람은 상대 별칭까지, 실패한 사람도 세션은 기록에 남는다
        var matched = history.history(sender).stream().filter(item -> item.sessionId().equals(sessionId)).findFirst().orElseThrow();
        assertThat(matched.matched()).isTrue();
        assertThat(matched.partnerMemberId()).isEqualTo(receiver);
        assertThat(matched.partnerAlias()).isEqualTo("alias-" + receiver);
        var failed = history.history(80003L).stream().filter(item -> item.sessionId().equals(sessionId)).findFirst().orElseThrow();
        assertThat(failed.matched()).isFalse();
        assertThat(failed.partnerAlias()).isNull();
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

    @Test
    void rejectsOppositeParticipantWhoWasNeverInTheSameStartedRoom() {
        // 80006(여)은 아직 시작하지 않은 3라운드에서만 80001과 짝이라 대화한 적이 없다
        var outsider = SessionParticipant.apply(sessionId, 80006L, Gender.FEMALE, "alias-80006", start);
        outsider.attend(); participants.saveAndFlush(outsider);
        meet(3, false, new long[][]{{80001L, 80006L}});

        assertThatThrownBy(() -> service.submit(sessionId, sender, List.of(80006L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(sessionId, sender, List.of(receiver, 80006L)))
                .isInstanceOf(IllegalArgumentException.class);
        // 거절된 제출은 기록되지 않아 대화한 상대로 다시 낼 수 있다
        assertThat(service.ownVote(sessionId, sender).submitted()).isFalse();
        service.submit(sessionId, sender, List.of(receiver));
        assertThat(service.ownVote(sessionId, sender).rankedMemberIds()).containsExactly(receiver);
    }
}
