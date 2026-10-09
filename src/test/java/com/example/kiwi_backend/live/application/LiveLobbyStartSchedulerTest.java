package com.example.kiwi_backend.live.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class LiveLobbyStartSchedulerTest {

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final LiveSessionStartService liveSessionStartService = mock(LiveSessionStartService.class);
    private final LiveLobbyStartScheduler service = new LiveLobbyStartScheduler(taskScheduler, liveSessionStartService);

    @Test
    void schedulesOnlyOnceAndStartsAtLobbyDeadline() {
        Instant closeAt = Instant.parse("2026-10-09T12:01:00Z");
        when(taskScheduler.schedule(any(Runnable.class), eq(closeAt))).thenReturn(mock(ScheduledFuture.class));

        service.scheduleIfNeeded(10L, closeAt);
        service.scheduleIfNeeded(10L, closeAt);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, times(1)).schedule(task.capture(), eq(closeAt));
        task.getValue().run();
        verify(liveSessionStartService).startLiveSession(10L);

        service.scheduleIfNeeded(10L, closeAt);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), eq(closeAt));
    }
}
