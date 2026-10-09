package com.example.kiwi_backend.realtime.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveLobbyPresenceServiceTest {

    private static final Instant OPEN_AT = Instant.parse("2026-10-09T11:55:00Z");
    private static final Instant CLOSE_AT = Instant.parse("2026-10-09T12:01:00Z");
    private final LiveLobbyPresenceService service = new LiveLobbyPresenceService(
            Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void countsOneMemberWithTwoConnectionsUntilBothDisconnect() {
        service.join(10L, 101L, "tab-1", OPEN_AT, CLOSE_AT);
        service.join(10L, 101L, "tab-2", OPEN_AT, CLOSE_AT);

        service.disconnect("tab-1");
        assertThat(service.connectedMembers(10L)).containsExactly(101L);

        service.disconnect("tab-2");
        assertThat(service.connectedMembers(10L)).isEmpty();
    }

    @Test
    void closeCapturesOnlyMembersConnectedAtThatMoment() {
        service.join(10L, 101L, "ws-1", OPEN_AT, CLOSE_AT);
        service.join(10L, 102L, "ws-2", OPEN_AT, CLOSE_AT);
        service.disconnect("ws-2");

        assertThat(service.closeLobby(10L)).isEqualTo(Set.of(101L));

        service.disconnect("ws-1");
        assertThat(service.closeLobby(10L)).isEqualTo(Set.of(101L));
        service.join(10L, 101L, "ws-3", OPEN_AT, CLOSE_AT);
        assertThat(service.connectedMembers(10L)).isEqualTo(Set.of(101L));
        assertThatThrownBy(() -> service.join(10L, 102L, "ws-4", OPEN_AT, CLOSE_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSocketCannotRemainInTwoLobbies() {
        service.join(10L, 101L, "ws-1", OPEN_AT, CLOSE_AT);
        service.join(20L, 101L, "ws-1", OPEN_AT, CLOSE_AT);

        assertThat(service.connectedMembers(10L)).isEmpty();
        assertThat(service.connectedMembers(20L)).isEqualTo(Set.of(101L));
    }

    @Test
    void rejectsNewJoinAtOrAfterDeadline() {
        LiveLobbyPresenceService lateService = new LiveLobbyPresenceService(Clock.fixed(CLOSE_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> lateService.join(10L, 101L, "ws-1", OPEN_AT, CLOSE_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로비 마감 시각이 지났습니다");
    }

    @Test
    void rejectsJoinBeforeLobbyOpens() {
        LiveLobbyPresenceService earlyService = new LiveLobbyPresenceService(
                Clock.fixed(OPEN_AT.minusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> earlyService.join(10L, 101L, "ws-1", OPEN_AT, CLOSE_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로비 입장 가능 시각 전입니다");
        assertThat(earlyService.connectedMembers(10L)).isEmpty();
    }

    @Test
    void acceptsJoinExactlyWhenLobbyOpens() {
        LiveLobbyPresenceService openingService = new LiveLobbyPresenceService(Clock.fixed(OPEN_AT, ZoneOffset.UTC));

        openingService.join(10L, 101L, "ws-1", OPEN_AT, CLOSE_AT);

        assertThat(openingService.connectedMembers(10L)).containsExactly(101L);
    }

    @Test
    void allowsConfirmedAttendeeToReconnectAfterLobbyCloses() {
        Clock clock = org.mockito.Mockito.mock(Clock.class);
        AtomicReference<Instant> now = new AtomicReference<>(OPEN_AT);
        org.mockito.Mockito.when(clock.instant()).thenAnswer(ignored -> now.get());
        LiveLobbyPresenceService reconnectService = new LiveLobbyPresenceService(clock);
        reconnectService.join(10L, 101L, "ws-1", OPEN_AT, CLOSE_AT);
        now.set(CLOSE_AT.plusSeconds(1));
        reconnectService.closeLobby(10L);
        reconnectService.disconnect("ws-1");

        reconnectService.join(10L, 101L, "ws-2", OPEN_AT, CLOSE_AT);

        assertThat(reconnectService.connectedMembers(10L)).containsExactly(101L);
    }
}
