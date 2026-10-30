package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.query.SessionQueryService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;

class LiveSessionPollerTest {
    @Test
    void closesDueSessionsWithoutJoinAndContinuesAfterFailure() {
        var query = mock(SessionQueryService.class);
        var start = mock(LiveSessionStartService.class);
        var rounds = mock(LiveRoundSchedulerService.class);
        var compatibility = mock(CompatibilityPreparationService.class);
        Instant now = Instant.parse("2026-10-13T12:01:30Z");
        when(query.findSessionIdsDueForLobbyClose(now)).thenReturn(List.of(1L, 2L));
        when(query.findSessionIdsDueForFirstRound(now)).thenReturn(List.of(3L, 4L));
        when(compatibility.isPrepared(3L)).thenReturn(true);
        when(compatibility.isPrepared(4L)).thenReturn(true);
        doThrow(new IllegalStateException("failure")).when(start).startLiveSession(1L);
        when(rounds.startSessionRounds(3L)).thenThrow(new IllegalStateException("failure"));
        new LiveSessionPoller(query, start, rounds, compatibility, Clock.fixed(now, ZoneOffset.UTC)).poll();
        verify(start).startLiveSession(1L);
        verify(start).startLiveSession(2L);
        verify(rounds).startSessionRounds(3L);
        verify(rounds).startSessionRounds(4L);
        verify(compatibility).isPrepared(3L);
        verify(compatibility).isPrepared(4L);
        verify(compatibility, never()).requestPreparation(anyLong());
    }

    @Test
    void delaysFirstRoundUntilCompatibilityIsPrepared() {
        var query = mock(SessionQueryService.class);
        var start = mock(LiveSessionStartService.class);
        var rounds = mock(LiveRoundSchedulerService.class);
        var compatibility = mock(CompatibilityPreparationService.class);
        Instant now = Instant.parse("2026-10-13T12:01:30Z");
        when(query.findSessionIdsDueForFirstRound(now)).thenReturn(List.of(3L));
        when(compatibility.isPrepared(3L)).thenReturn(false);

        new LiveSessionPoller(query, start, rounds, compatibility, Clock.fixed(now, ZoneOffset.UTC)).poll();

        verify(compatibility).requestPreparation(3L);
        verifyNoInteractions(rounds);
    }
}
