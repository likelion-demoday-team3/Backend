package com.example.kiwi_backend.realtime.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class LiveLobbyDisconnectListener {

    private final LiveLobbyPresenceService lobbyPresenceService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        lobbyPresenceService.disconnect(event.getSessionId());
    }
}
