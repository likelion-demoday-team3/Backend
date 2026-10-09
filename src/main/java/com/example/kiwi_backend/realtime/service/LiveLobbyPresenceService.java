package com.example.kiwi_backend.realtime.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class LiveLobbyPresenceService {

    private final Clock clock;
    private final Map<String, Connection> connections = new HashMap<>();
    private final Map<Long, Lobby> lobbies = new HashMap<>();

    public LiveLobbyPresenceService(Clock clock) {
        this.clock = clock;
    }

    public synchronized void join(Long sessionId, Long memberId, String websocketSessionId,
                                  Instant openAt, Instant closeAt) {
        if (sessionId == null || memberId == null || websocketSessionId == null || websocketSessionId.isBlank()
                || openAt == null || closeAt == null || !openAt.isBefore(closeAt)) {
            throw new IllegalArgumentException("sessionId, memberId, websocketSessionId and a valid lobby window are required");
        }

        Lobby lobby = lobbies.computeIfAbsent(sessionId, ignored -> new Lobby());
        if (lobby.attendees != null && !lobby.attendees.contains(memberId)) {
            throw new IllegalStateException("로비가 마감되어 새 참가자는 입장할 수 없습니다");
        }
        if (lobby.attendees == null) {
            Instant now = clock.instant();
            if (now.isBefore(openAt)) {
                throw new IllegalStateException("로비 입장 가능 시각 전입니다");
            }
            if (!now.isBefore(closeAt)) {
                throw new IllegalStateException("로비 마감 시각이 지났습니다");
            }
        }

        disconnect(websocketSessionId);
        connections.put(websocketSessionId, new Connection(sessionId, memberId));
        lobby.connected.computeIfAbsent(memberId, ignored -> new HashSet<>()).add(websocketSessionId);
    }

    public synchronized void disconnect(String websocketSessionId) {
        Connection connection = connections.remove(websocketSessionId);
        if (connection == null) {
            return;
        }
        Lobby lobby = lobbies.get(connection.sessionId);
        Set<String> memberConnections = lobby.connected.get(connection.memberId);
        memberConnections.remove(websocketSessionId);
        if (memberConnections.isEmpty()) {
            lobby.connected.remove(connection.memberId);
        }
    }

    public synchronized Set<Long> connectedMembers(Long sessionId) {
        Lobby lobby = lobbies.get(sessionId);
        return lobby == null ? Set.of() : Set.copyOf(lobby.connected.keySet());
    }

    public synchronized Set<Long> closeLobby(Long sessionId) {
        Lobby lobby = lobbies.computeIfAbsent(sessionId, ignored -> new Lobby());
        if (lobby.attendees == null) {
            lobby.attendees = Set.copyOf(lobby.connected.keySet());
        }
        return lobby.attendees;
    }

    private record Connection(Long sessionId, Long memberId) {
    }

    private static class Lobby {
        private final Map<Long, Set<String>> connected = new HashMap<>();
        private Set<Long> attendees;
    }
}
