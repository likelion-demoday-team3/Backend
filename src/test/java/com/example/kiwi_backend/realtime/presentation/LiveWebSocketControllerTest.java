package com.example.kiwi_backend.realtime.presentation;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.realtime.dto.LiveJoinRequest;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.LiveJoinStatus;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
import com.example.kiwi_backend.realtime.dto.SignalType;
import com.example.kiwi_backend.realtime.service.LiveJoinService;
import com.example.kiwi_backend.realtime.service.RealtimeSignalService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveWebSocketControllerTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RealtimeSignalService realtimeSignalService = mock(RealtimeSignalService.class);
    private final LiveJoinService liveJoinService = mock(LiveJoinService.class);
    private final LiveWebSocketController controller = new LiveWebSocketController(
            messagingTemplate,
            realtimeSignalService,
            liveJoinService
    );

    @Test
    void joinSendsRestoredJoinResponseToAuthenticatedUser() {
        LiveJoinRequest request = new LiveJoinRequest(10L);
        AuthUser authUser = new AuthUser(101L, "USER");
        LiveJoinResponse response = new LiveJoinResponse(
                10L,
                101L,
                LiveJoinStatus.JOINED,
                null,
                null,
                null,
                null,
                null
        );

        when(liveJoinService.join(10L, 101L)).thenReturn(response);

        controller.join(request, authUser);

        verify(messagingTemplate).convertAndSendToUser("101", "/queue/live/joined", response);
    }

    @Test
    void signalDelegatesValidationAndSendToService() {
        SignalRequest request = new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp");
        AuthUser authUser = new AuthUser(101L, "USER");

        controller.signal(request, authUser);

        verify(realtimeSignalService).sendSignal(request, 101L);
    }
}
