package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.live.api.dto.LiveRoundAdvanceResponse;
import com.example.kiwi_backend.live.api.dto.LiveRoundStateResponse;
import com.example.kiwi_backend.live.application.LiveRoundAdvanceResult;
import com.example.kiwi_backend.live.application.LiveRoundStateResult;
import com.example.kiwi_backend.live.application.LiveRoundStateService;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRoundControlControllerTest {

    private final LiveRoundStateService liveRoundStateService = mock(LiveRoundStateService.class);
    private final LiveRoundControlController controller = new LiveRoundControlController(liveRoundStateService);

    @Test
    void startRoundCallsStateService() {
        when(liveRoundStateService.startRound(eq(10L), eq(1), any(Instant.class)))
                .thenReturn(new LiveRoundStateResult(10L, 1, LiveRoundStatus.IN_PROGRESS, true));

        ApiResponse<LiveRoundStateResponse> response = controller.startRound(10L, 1);

        assertThat(response.success()).isTrue();
        assertThat(response.data().status()).isEqualTo(LiveRoundStatus.IN_PROGRESS);
        assertThat(response.data().assignmentBroadcasted()).isTrue();
        verify(liveRoundStateService).startRound(eq(10L), eq(1), any(Instant.class));
    }

    @Test
    void completeRoundCallsStateService() {
        when(liveRoundStateService.completeRound(eq(10L), eq(1), any(Instant.class)))
                .thenReturn(new LiveRoundStateResult(10L, 1, LiveRoundStatus.COMPLETED, false));

        ApiResponse<LiveRoundStateResponse> response = controller.completeRound(10L, 1);

        assertThat(response.success()).isTrue();
        assertThat(response.data().status()).isEqualTo(LiveRoundStatus.COMPLETED);
        assertThat(response.data().assignmentBroadcasted()).isFalse();
        verify(liveRoundStateService).completeRound(eq(10L), eq(1), any(Instant.class));
    }

    @Test
    void advanceRoundCallsStateService() {
        when(liveRoundStateService.completeAndStartNextRound(eq(10L), eq(1), any(Instant.class), any(Instant.class)))
                .thenReturn(new LiveRoundAdvanceResult(10L, 1, true, 2));

        ApiResponse<LiveRoundAdvanceResponse> response = controller.advanceRound(10L, 1);

        assertThat(response.success()).isTrue();
        assertThat(response.data().completedRoundNumber()).isEqualTo(1);
        assertThat(response.data().nextRoundStarted()).isTrue();
        assertThat(response.data().nextRoundNumber()).isEqualTo(2);
        verify(liveRoundStateService)
                .completeAndStartNextRound(eq(10L), eq(1), any(Instant.class), any(Instant.class));
    }
}
