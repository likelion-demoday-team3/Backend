package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.contract.query.SessionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IceServerServiceTest {

    private final SessionQueryService sessionQuery = mock(SessionQueryService.class);
    private IceServerService service;

    @BeforeEach
    void setUp() {
        service = new IceServerService(sessionQuery, Clock.fixed(
                Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "stunUrls", "stun:stun.example.test:3478");
        ReflectionTestUtils.setField(service, "turnUrls", "turn:turn.example.test:3478, turns:turn.example.test:5349");
        ReflectionTestUtils.setField(service, "turnSharedSecret", "test-secret");
        ReflectionTestUtils.setField(service, "credentialTtlSeconds", 600);
    }

    @Test
    void returnsShortLivedTurnCredentialsOnlyToSessionParticipants() throws Exception {
        when(sessionQuery.isParticipant(8L, 12L)).thenReturn(true);

        var response = service.getForMember(8L, 12L);

        var turn = response.iceServers().get(1);
        assertThat(turn.urls()).containsExactly("turn:turn.example.test:3478", "turns:turn.example.test:5349");
        assertThat(turn.username()).isEqualTo("1791893400:12");
        Mac hmac = Mac.getInstance("HmacSHA1");
        hmac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        assertThat(turn.credential()).isEqualTo(Base64.getEncoder().encodeToString(
                hmac.doFinal(turn.username().getBytes(StandardCharsets.UTF_8))));
        assertThat(response.credentialTtlSeconds()).isEqualTo(600);
    }

    @Test
    void rejectsNonParticipants() {
        when(sessionQuery.isParticipant(8L, 12L)).thenReturn(false);
        assertThatThrownBy(() -> service.getForMember(8L, 12L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsStunOnlyWhenTurnIsNotConfigured() {
        when(sessionQuery.isParticipant(8L, 12L)).thenReturn(true);
        ReflectionTestUtils.setField(service, "turnUrls", " ");
        var response = service.getForMember(8L, 12L);
        assertThat(response.iceServers()).hasSize(1);
        assertThat(response.iceServers().getFirst().username()).isNull();
        assertThat(response.credentialTtlSeconds()).isZero();
    }
}
