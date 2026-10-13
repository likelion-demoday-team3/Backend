package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class LiveFirstRoundTransactionTest {
    @Autowired LiveRoundSchedulerService scheduler;
    @Autowired DatingSessionRepository sessions;
    @Autowired LiveRoundRepository rounds;
    @MockitoBean LiveRoundBroadcastService broadcast;
    @MockitoBean(name = "taskScheduler") TaskScheduler tasks;

    private DatingSession waitingSession() {
        Instant now = Instant.now();
        Instant createdAt = now.minusSeconds(86400);
        DatingSession session = DatingSession.create(now.minusSeconds(120), 3, 180, 120, 300, 12, 2, createdAt);
        session.openRecruiting(createdAt);
        session.markReady();
        session.startProgress();
        return sessions.saveAndFlush(session);
    }

    @Test
    void missingRoundRollsBackSessionStateAndCanRetry() {
        DatingSession session = waitingSession();
        assertThatThrownBy(() -> scheduler.startSessionRounds(session.getId()))
                .hasMessage("round not found");
        assertThat(sessions.findById(session.getId()).orElseThrow().getLiveStatus()).isEqualTo(LiveStatus.WAITING);
        verifyNoInteractions(tasks, broadcast);

        rounds.saveAndFlush(LiveRound.create(session.getId(), 1));
        scheduler.startSessionRounds(session.getId());
        assertThat(sessions.findById(session.getId()).orElseThrow().getLiveStatus()).isEqualTo(LiveStatus.IN_ROUND);
        verify(tasks).schedule(any(Runnable.class), any(Instant.class));
        verify(broadcast).broadcastRoundAssignments(session.getId(), 1);
        assertThatThrownBy(() -> scheduler.startSessionRounds(session.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(tasks, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }
}
