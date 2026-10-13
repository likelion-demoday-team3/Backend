package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.live.application.LiveSessionStartService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LiveSessionStartControllerTest {
    @Test
    void returnsCommonAcknowledgement() {
        var service = mock(LiveSessionStartService.class);
        var response = new LiveSessionStartController(service).startLiveSession(10L);
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
        verify(service).startLiveSession(10L);
    }
}
