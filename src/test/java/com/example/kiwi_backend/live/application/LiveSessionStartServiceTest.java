package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionAttendanceCommand;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.LiveParticipant;
import com.example.kiwi_backend.live.domain.RotationTableGenerator;
import com.example.kiwi_backend.realtime.service.LiveLobbyPresenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSessionStartServiceTest {

    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider = mock(ObjectProvider.class);
    private final SessionQueryService sessionQueryService = mock(SessionQueryService.class);
    private final ObjectProvider<SessionAttendanceCommand> attendanceCommandProvider = mock(ObjectProvider.class);
    private final SessionAttendanceCommand attendanceCommand = mock(SessionAttendanceCommand.class);
    private final LiveLobbyPresenceService lobbyPresenceService = mock(LiveLobbyPresenceService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T12:01:00Z"), ZoneOffset.UTC);
    private final LiveScheduleService liveScheduleService = mock(LiveScheduleService.class);
    private final LiveRoundSchedulerService liveRoundSchedulerService = mock(LiveRoundSchedulerService.class);

    private final LiveSessionStartService service = new LiveSessionStartService(
            sessionQueryServiceProvider,
            attendanceCommandProvider,
            lobbyPresenceService,
            clock,
            new RotationTableGenerator(),
            liveScheduleService,
            liveRoundSchedulerService
    );

    @Test
    void startLiveSessionCreatesScheduleAndStartsScheduler() {
        List<LiveParticipant> participants = List.of(
                new LiveParticipant(1L, Gender.MALE),
                new LiveParticipant(2L, Gender.MALE),
                new LiveParticipant(3L, Gender.MALE),
                new LiveParticipant(10L, Gender.FEMALE),
                new LiveParticipant(11L, Gender.FEMALE),
                new LiveParticipant(12L, Gender.FEMALE)
        );
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(sessionQueryService);
        when(sessionQueryService.getStartTime(10L)).thenReturn(Instant.parse("2026-10-09T12:00:00Z"));
        when(attendanceCommandProvider.getIfAvailable()).thenReturn(attendanceCommand);
        when(lobbyPresenceService.closeLobby(10L)).thenReturn(Set.of(1L, 2L, 3L, 10L, 11L, 12L));
        when(sessionQueryService.getLiveParticipants(10L)).thenReturn(participants);
        when(liveScheduleService.createSchedule(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new LiveScheduleResult(10L, 3, 9, 0));
        when(liveRoundSchedulerService.startSessionRounds(10L))
                .thenReturn(new LiveRoundSchedulerResult(10L, 1, 180));

        LiveSessionStartResult result = service.startLiveSession(10L);

        assertThat(result.sessionId()).isEqualTo(10L);
        assertThat(result.participantCount()).isEqualTo(6);
        assertThat(result.roundCount()).isEqualTo(3);
        assertThat(result.roomCount()).isEqualTo(9);
        assertThat(result.idleCount()).isZero();
        assertThat(result.firstRoundNumber()).isEqualTo(1);
        assertThat(result.roundDurationSeconds()).isEqualTo(180);

        ArgumentCaptor<List<LiveRoundPlan>> plansCaptor = ArgumentCaptor.forClass(List.class);
        verify(liveScheduleService).createSchedule(org.mockito.ArgumentMatchers.eq(10L), plansCaptor.capture());
        assertThat(plansCaptor.getValue()).hasSize(3);
        assertThat(plansCaptor.getValue().get(0).rooms()).hasSize(3);
        assertThat(plansCaptor.getValue().get(0).idleMemberIds()).isEmpty();
        verify(liveRoundSchedulerService).startSessionRounds(10L);
        verify(attendanceCommand).finalizeAttendance(10L, Set.of(1L, 2L, 3L, 10L, 11L, 12L));
    }

    @Test
    void startLiveSessionRejectsMissingSessionQueryService() {
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.startLiveSession(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SessionQueryService 구현체가 필요합니다");
    }

    @Test
    void startLiveSessionRejectsEarlyLobbyClose() {
        when(sessionQueryServiceProvider.getIfAvailable()).thenReturn(sessionQueryService);
        when(sessionQueryService.getStartTime(10L)).thenReturn(Instant.parse("2026-10-09T12:00:01Z"));

        assertThatThrownBy(() -> service.startLiveSession(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로비 마감 시각 전에는 세션을 시작할 수 없습니다");
        org.mockito.Mockito.verifyNoInteractions(lobbyPresenceService);
    }
}
