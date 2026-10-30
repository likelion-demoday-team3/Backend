package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionAttendanceCommand;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.RotationTableGenerator;
import com.example.kiwi_backend.realtime.service.LiveLobbyPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LiveSessionStartServiceTest {
    private final ObjectProvider<SessionQueryService> queries = mock(ObjectProvider.class);
    private final ObjectProvider<SessionAttendanceCommand> commands = mock(ObjectProvider.class);
    private final SessionQueryService query = mock(SessionQueryService.class);
    private final SessionAttendanceCommand command = mock(SessionAttendanceCommand.class);
    private final LiveLobbyPresenceService presence = mock(LiveLobbyPresenceService.class);
    private final LiveScheduleService schedules = mock(LiveScheduleService.class);
    private final LiveSessionNoticeService notices = mock(LiveSessionNoticeService.class);
    private final Instant now = Instant.parse("2026-10-13T12:01:00Z");
    private final LiveSessionStartService service = new LiveSessionStartService(queries, commands,
            presence, Clock.fixed(now, ZoneOffset.UTC), new RotationTableGenerator(), schedules, notices);
    private final List<LiveParticipant> participants = List.of(
            new LiveParticipant(1L, Gender.MALE, "a"), new LiveParticipant(2L, Gender.MALE, "b"),
            new LiveParticipant(3L, Gender.MALE, "c"), new LiveParticipant(4L, Gender.FEMALE, "d"),
            new LiveParticipant(5L, Gender.FEMALE, "e"));

    @BeforeEach
    void setup() {
        when(queries.getIfAvailable()).thenReturn(query);
        when(commands.getIfAvailable()).thenReturn(command);
        when(query.getLobbyCloseAt(10L)).thenReturn(now);
        when(presence.closeLobby(10L)).thenReturn(Set.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void savesAttendanceSnapshotAndAnnouncesScheduledStart() {
        when(command.finalizeAttendance(eq(10L), anySet()))
                .thenReturn(AttendanceResult.proceeded(10L, participants, now.plusSeconds(30)));
        service.startLiveSession(10L);
        verify(schedules).createSchedule(eq(10L), argThat(plans -> plans.size() == 3
                && plans.stream().allMatch(p -> p.rooms().size() == 2 && p.idleMemberIds().size() == 1)));
        verify(notices).startingSoonAfterCommit(10L, participants, now.plusSeconds(30));
        verify(query, never()).getLiveParticipants(anyLong());
    }

    @Test
    void testCallUsesTheExplicitTwoPartnerOrderWithThreeAttendees() {
        List<LiveParticipant> testParticipants = List.of(
                new LiveParticipant(7L, Gender.MALE, "host"),
                new LiveParticipant(12L, Gender.FEMALE, "first"),
                new LiveParticipant(15L, Gender.FEMALE, "second"));
        when(query.isCallTestSession(10L)).thenReturn(true);
        when(query.getCallTestParticipantOrder(10L))
                .thenReturn(new CallTestParticipantOrder(7L, 12L, 15L));
        when(command.finalizeAttendance(eq(10L), anySet()))
                .thenReturn(AttendanceResult.proceeded(10L, testParticipants, now.plusSeconds(30)));

        service.startLiveSession(10L);

        verify(schedules).createSchedule(eq(10L), argThat(plans -> plans.size() == 2
                && plans.get(0).rooms().get(0).maleMemberId().equals(7L)
                && plans.get(0).rooms().get(0).femaleMemberId().equals(12L)
                && plans.get(1).rooms().get(0).femaleMemberId().equals(15L)));
    }

    @Test
    void cancellationDoesNotGenerateSchedule() {
        when(command.finalizeAttendance(eq(10L), anySet())).thenReturn(AttendanceResult.cancelled(10L));
        service.startLiveSession(10L);
        verifyNoInteractions(schedules);
        verify(notices).cancelledAfterCommit(10L, Set.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void repeatedCloseDoesNotSaveOrNotifyAgain() {
        when(command.finalizeAttendance(eq(10L), anySet())).thenReturn(AttendanceResult.alreadyProcessed(10L));
        service.startLiveSession(10L);
        verifyNoInteractions(schedules, notices);
    }

    @Test
    void earlyRequestDoesNotFreezePresence() {
        when(query.getLobbyCloseAt(10L)).thenReturn(now.plusSeconds(1));
        assertThatThrownBy(() -> service.startLiveSession(10L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(presence, command, schedules, notices);
    }

    @Test
    void saveFailureDoesNotAnnounceStart() {
        when(command.finalizeAttendance(eq(10L), anySet()))
                .thenReturn(AttendanceResult.proceeded(10L, participants, now.plusSeconds(30)));
        when(schedules.createSchedule(anyLong(), anyList())).thenThrow(new IllegalStateException("database failure"));
        assertThatThrownBy(() -> service.startLiveSession(10L)).hasMessage("database failure");
        verifyNoInteractions(notices);
    }
}
