package com.example.kiwi_backend.realtime.presentation;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.application.LiveLobbyStartScheduler;
import com.example.kiwi_backend.realtime.dto.LiveJoinRequest;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.LiveJoinStatus;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
import com.example.kiwi_backend.realtime.dto.SignalType;
import com.example.kiwi_backend.realtime.service.LiveJoinService;
import com.example.kiwi_backend.realtime.service.LiveLobbyPresenceService;
import com.example.kiwi_backend.realtime.service.RealtimeSignalService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveWebSocketControllerTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RealtimeSignalService realtimeSignalService = mock(RealtimeSignalService.class);
    private final LiveJoinService liveJoinService = mock(LiveJoinService.class);
    private final LiveLobbyPresenceService lobbyPresenceService = mock(LiveLobbyPresenceService.class);
    private final LiveLobbyStartScheduler lobbyStartScheduler = mock(LiveLobbyStartScheduler.class);
    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider = mock(ObjectProvider.class);
    private final SessionQueryService sessionQueryService = mock(SessionQueryService.class);
    private final LiveWebSocketController controller = new LiveWebSocketController(
            messagingTemplate,
            realtimeSignalService,
            liveJoinService,
            lobbyPresenceService,
            lobbyStartScheduler,
            sessionQueryServiceProvider
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
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(sessionQueryService);
        when(sessionQueryService.getStartTime(10L)).thenReturn(Instant.parse("2026-10-09T12:00:00Z"));

        controller.join(request, authUser, "ws-1");

        verify(lobbyPresenceService).join(10L, 101L, "ws-1",
                Instant.parse("2026-10-09T11:55:00Z"), Instant.parse("2026-10-09T12:01:00Z"));
        verify(lobbyStartScheduler).scheduleIfNeeded(10L, Instant.parse("2026-10-09T12:01:00Z"));
        verify(messagingTemplate).convertAndSendToUser("101", "/queue/live/joined", response);
    }

    @Test
    void signalDelegatesValidationAndSendToService() {
        SignalRequest request = new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp");
        AuthUser authUser = new AuthUser(101L, "USER");

        controller.signal(request, authUser);

        verify(realtimeSignalService).sendSignal(request, 101L);
    }

    @Test
    void signalRejectsUnauthenticatedSender() {
        SignalRequest request = new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp");

        assertThatThrownBy(() -> controller.signal(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authenticated member is required");

        verifyNoInteractions(realtimeSignalService);
    }
}
