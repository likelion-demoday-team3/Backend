package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.live.api.dto.LiveRoundSchedulerResponse;
import com.example.kiwi_backend.live.application.LiveRoundSchedulerResult;
import com.example.kiwi_backend.live.application.LiveRoundSchedulerService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveRoundSchedulerControllerTest {

    private final LiveRoundSchedulerService liveRoundSchedulerService = mock(LiveRoundSchedulerService.class);
    private final LiveRoundSchedulerController controller = new LiveRoundSchedulerController(liveRoundSchedulerService);

    @Test
    void startSchedulerReturnsCommonApiResponse() {
        when(liveRoundSchedulerService.startSessionRounds(10L))
                .thenReturn(new LiveRoundSchedulerResult(10L, 1, 180));

        ApiResponse<LiveRoundSchedulerResponse> response = controller.startScheduler(10L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().sessionId()).isEqualTo(10L);
        assertThat(response.data().firstRoundNumber()).isEqualTo(1);
        assertThat(response.data().roundDurationSeconds()).isEqualTo(180);
    }
}
