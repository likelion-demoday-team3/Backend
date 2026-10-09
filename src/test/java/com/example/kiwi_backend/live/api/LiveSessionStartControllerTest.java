package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.live.api.dto.LiveSessionStartResponse;
import com.example.kiwi_backend.live.application.LiveSessionStartResult;
import com.example.kiwi_backend.live.application.LiveSessionStartService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveSessionStartControllerTest {

    private final LiveSessionStartService liveSessionStartService = mock(LiveSessionStartService.class);
    private final LiveSessionStartController controller = new LiveSessionStartController(liveSessionStartService);

    @Test
    void startLiveSessionReturnsCommonApiResponse() {
        when(liveSessionStartService.startLiveSession(10L))
                .thenReturn(new LiveSessionStartResult(10L, 6, 3, 9, 0, 1, 180));

        ApiResponse<LiveSessionStartResponse> response = controller.startLiveSession(10L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().sessionId()).isEqualTo(10L);
        assertThat(response.data().participantCount()).isEqualTo(6);
        assertThat(response.data().roundCount()).isEqualTo(3);
        assertThat(response.data().roomCount()).isEqualTo(9);
        assertThat(response.data().idleCount()).isZero();
        assertThat(response.data().firstRoundNumber()).isEqualTo(1);
        assertThat(response.data().roundDurationSeconds()).isEqualTo(180);
    }
}
