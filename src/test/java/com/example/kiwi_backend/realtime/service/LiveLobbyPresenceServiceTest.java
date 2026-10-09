package com.example.kiwi_backend.realtime.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveLobbyPresenceServiceTest {

    private static final Instant CLOSE_AT = Instant.parse("2026-10-09T12:01:00Z");
    private final LiveLobbyPresenceService service = new LiveLobbyPresenceService(
            Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void countsOneMemberWithTwoConnectionsUntilBothDisconnect() {
        service.join(10L, 101L, "tab-1", CLOSE_AT);
        service.join(10L, 101L, "tab-2", CLOSE_AT);

        service.disconnect("tab-1");
        assertThat(service.connectedMembers(10L)).containsExactly(101L);

        service.disconnect("tab-2");
        assertThat(service.connectedMembers(10L)).isEmpty();
    }

    @Test
    void closeCapturesOnlyMembersConnectedAtThatMoment() {
        service.join(10L, 101L, "ws-1", CLOSE_AT);
        service.join(10L, 102L, "ws-2", CLOSE_AT);
        service.disconnect("ws-2");

        assertThat(service.closeLobby(10L)).isEqualTo(Set.of(101L));

        service.disconnect("ws-1");
        assertThat(service.closeLobby(10L)).isEqualTo(Set.of(101L));
        service.join(10L, 101L, "ws-3", CLOSE_AT);
        assertThat(service.connectedMembers(10L)).isEqualTo(Set.of(101L));
        assertThatThrownBy(() -> service.join(10L, 102L, "ws-4", CLOSE_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSocketCannotRemainInTwoLobbies() {
        service.join(10L, 101L, "ws-1", CLOSE_AT);
        service.join(20L, 101L, "ws-1", CLOSE_AT);

        assertThat(service.connectedMembers(10L)).isEmpty();
        assertThat(service.connectedMembers(20L)).isEqualTo(Set.of(101L));
    }

    @Test
    void rejectsNewJoinAtOrAfterDeadline() {
        LiveLobbyPresenceService lateService = new LiveLobbyPresenceService(Clock.fixed(CLOSE_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> lateService.join(10L, 101L, "ws-1", CLOSE_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로비 마감 시각이 지났습니다");
    }
}
