package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

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
    private final LiveVotingStartService liveVotingStartService = mock(LiveVotingStartService.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private final LiveRoundSchedulerService service = new LiveRoundSchedulerService(
            liveRoundRepository,
            liveRoundStateService,
            liveVotingStartService,
            taskScheduler
    );

    @Test
    void startSessionRoundsStartsFirstRoundAndSchedulesEnd() {
        ReflectionTestUtils.setField(service, "roundDurationSeconds", 180);
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1), LiveRound.create(10L, 2)));

        LiveRoundSchedulerResult result = service.startSessionRounds(10L);

        assertThat(result.sessionId()).isEqualTo(10L);
        assertThat(result.firstRoundNumber()).isEqualTo(1);
        assertThat(result.roundDurationSeconds()).isEqualTo(180);

        verify(liveRoundStateService).startRound(eq(10L), eq(1), any(Instant.class));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void scheduledEndPreparesNextRoundAndSchedulesNextStart() {
        ReflectionTestUtils.setField(service, "roundDurationSeconds", 180);
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1), LiveRound.create(10L, 2)));
        when(liveRoundStateService.completeAndPrepareNextRound(eq(10L), eq(1), any(Instant.class)))
                .thenReturn(new LiveRoundAdvanceResult(10L, 1, true, 2, LiveRoundPhase.BREAK, 60));

        service.startSessionRounds(10L);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));

        taskCaptor.getValue().run();

        verify(liveRoundStateService).completeAndPrepareNextRound(eq(10L), eq(1), any(Instant.class));
        verify(taskScheduler, org.mockito.Mockito.times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void startSessionRoundsRejectsAlreadyRunningSession() {
        ReflectionTestUtils.setField(service, "roundDurationSeconds", 180);
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1)));

        service.startSessionRounds(10L);

        assertThatThrownBy(() -> service.startSessionRounds(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 자동 진행 중인 라이브 세션입니다");
    }

    @Test
    void startSessionRoundsRejectsSessionWithoutRounds() {
        ReflectionTestUtils.setField(service, "roundDurationSeconds", 180);
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startSessionRounds(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("round not found");
    }

    @Test
    void lastRoundStartsVotingInsteadOfSchedulingAnotherRound() {
        ReflectionTestUtils.setField(service, "roundDurationSeconds", 180);
        when(liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(10L))
                .thenReturn(List.of(LiveRound.create(10L, 1)));
        when(liveRoundStateService.completeAndPrepareNextRound(eq(10L), eq(1), any(Instant.class)))
                .thenReturn(new LiveRoundAdvanceResult(10L, 1, false, null, LiveRoundPhase.FINISHED, 0));

        service.startSessionRounds(10L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));

        taskCaptor.getValue().run();

        verify(liveVotingStartService).startVoting(eq(10L), any(Instant.class));
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }
}
