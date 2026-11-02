package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.example.kiwi_backend.contract.command.SessionLiveStatusCommand;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRoundSchedulerServiceTest {

    private final LiveRoundRepository liveRoundRepository = mock(LiveRoundRepository.class);
    private final LiveRoundStateService liveRoundStateService = mock(LiveRoundStateService.class);
    private final LiveHeartPhaseService liveHeartPhaseService = mock(LiveHeartPhaseService.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final SessionLiveStatusCommand statusCommand = mock(SessionLiveStatusCommand.class);
    private final SessionQueryService sessionQueryService = mock(SessionQueryService.class);

    private final LiveRoundSchedulerService service = new LiveRoundSchedulerService(
            liveRoundRepository,
            liveRoundStateService,
            liveHeartPhaseService,
            taskScheduler,
            statusCommand,
            sessionQueryService
    );

    @BeforeEach
    void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        when(sessionQueryService.getRoundDurationSeconds(10L)).thenReturn(600);
    }

    @AfterEach
    void cleanupTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private LiveRoundSchedulerResult startAndCommit() {
        LiveRoundSchedulerResult result = service.startSessionRounds(10L);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        return result;
    }

    @Test
    void startSessionRoundsStartsFirstRoundAndSchedulesEnd() {
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1), LiveRound.create(10L, 2)));

        Instant before = Instant.now();
        LiveRoundSchedulerResult result = startAndCommit();
        Instant after = Instant.now();

        assertThat(result.sessionId()).isEqualTo(10L);
        assertThat(result.firstRoundNumber()).isEqualTo(1);
        assertThat(result.roundDurationSeconds()).isEqualTo(600);

        verify(liveRoundStateService).startRound(eq(10L), eq(1), any(Instant.class));
        ArgumentCaptor<Instant> endAt = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), endAt.capture());
        assertThat(endAt.getValue()).isBetween(before.plusSeconds(600), after.plusSeconds(600));
    }

    @Test
    void scheduledEndPreparesNextRoundAndSchedulesNextStart() {
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1), LiveRound.create(10L, 2)));
        when(liveRoundStateService.completeAndPrepareNextRound(eq(10L), eq(1), any(Instant.class)))
                .thenReturn(new LiveRoundAdvanceResult(10L, 1, true, 2, LiveRoundPhase.BREAK, 60));

        startAndCommit();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));

        taskCaptor.getValue().run();

        verify(liveRoundStateService).completeAndPrepareNextRound(eq(10L), eq(1), any(Instant.class));
        verify(taskScheduler, org.mockito.Mockito.times(2)).schedule(any(Runnable.class), any(Instant.class));

        ArgumentCaptor<Runnable> nextStart = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, org.mockito.Mockito.times(2)).schedule(nextStart.capture(), any(Instant.class));
        Instant beforeNextRound = Instant.now();
        nextStart.getAllValues().get(1).run();
        Instant afterNextRound = Instant.now();

        verify(liveRoundStateService).startRound(eq(10L), eq(2), any(Instant.class));
        ArgumentCaptor<Instant> scheduledAt = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, org.mockito.Mockito.times(3)).schedule(any(Runnable.class), scheduledAt.capture());
        assertThat(scheduledAt.getAllValues().get(2))
                .isBetween(beforeNextRound.plusSeconds(600), afterNextRound.plusSeconds(600));
    }

    @Test
    void startSessionRoundsRejectsAlreadyRunningSession() {
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1)));

        startAndCommit();

        assertThatThrownBy(() -> service.startSessionRounds(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 자동 진행 중인 라이브 세션입니다");
    }

    @Test
    void startSessionRoundsRejectsSessionWithoutRounds() {
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startSessionRounds(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("round not found");
    }

    @Test
    void lastRoundStartsHeartsInsteadOfSchedulingAnotherRound() {
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1)));
        when(liveRoundStateService.completeAndPrepareNextRound(eq(10L), eq(1), any(Instant.class)))
                .thenReturn(new LiveRoundAdvanceResult(10L, 1, false, null, LiveRoundPhase.FINISHED, 0));

        startAndCommit();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));

        taskCaptor.getValue().run();

        verify(liveHeartPhaseService).startHearts(eq(10L), any(Instant.class));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void testSessionUsesItsOwnThreeMinuteDuration() {
        when(sessionQueryService.getRoundDurationSeconds(10L)).thenReturn(180);
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1)));

        Instant before = Instant.now();
        LiveRoundSchedulerResult result = startAndCommit();
        Instant after = Instant.now();

        assertThat(result.roundDurationSeconds()).isEqualTo(180);
        ArgumentCaptor<Instant> endAt = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), endAt.capture());
        assertThat(endAt.getValue()).isBetween(before.plusSeconds(180), after.plusSeconds(180));
    }
}
