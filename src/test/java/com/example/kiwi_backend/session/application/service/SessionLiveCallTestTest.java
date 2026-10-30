package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.AttendanceResult;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionLiveCallTestTest {

    @Test
    void regularAndCallTestSessionsBothCloseLobbyAtStartAndBeginFirstRoundFifteenSecondsLater() {
        Instant createdAt = Instant.parse("2026-10-29T10:00:00Z");
        Instant startTime = createdAt.plusSeconds(180);
        DatingSession test = DatingSession.createCallTest(startTime, 1L, 2L, 3L, createdAt);
        DatingSession regular = DatingSession.create(startTime, 3, 180, 120, 600, 0, 0, createdAt);

        assertThat(test.lobbyCloseAt()).isEqualTo(startTime);
        assertThat(test.firstRoundAt()).isEqualTo(startTime.plusSeconds(15));
        assertThat(test.getPhaseEndsAt()).isEqualTo(test.firstRoundAt());
        assertThat(regular.lobbyCloseAt()).isEqualTo(startTime);
        assertThat(regular.firstRoundAt()).isEqualTo(startTime.plusSeconds(15));
    }

    @Test
    void proceedsForAllThreeExpectedCallTestAttendees() {
        Instant createdAt = Instant.parse("2026-10-29T10:00:00Z");
        Instant startTime = createdAt.plusSeconds(180);
        DatingSession session = DatingSession.createCallTest(startTime, 1L, 2L, 3L, createdAt);
        var participants = List.of(
                SessionParticipant.apply(8L, 1L, Gender.MALE, "host", createdAt),
                SessionParticipant.apply(8L, 2L, Gender.FEMALE, "first", createdAt),
                SessionParticipant.apply(8L, 3L, Gender.FEMALE, "second", createdAt));
        Instant closeAt = session.lobbyCloseAt();
        var sessionRepository = mock(DatingSessionRepository.class);
        var participantRepository = mock(SessionParticipantRepository.class);
        when(sessionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(session));
        when(participantRepository.findAllBySessionIdAndStatus(8L, ParticipantStatus.APPLIED))
                .thenReturn(participants);
        SessionLiveService service = new SessionLiveService(sessionRepository, participantRepository,
                mock(GemWallet.class), Clock.fixed(closeAt, ZoneOffset.UTC));

        AttendanceResult result = service.finalizeAttendance(8L, Set.of(1L, 2L, 3L));

        assertThat(result.proceed()).isTrue();
        assertThat(result.attendees()).hasSize(3);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(participants).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ParticipantStatus.ATTENDED));
    }

    @Test
    void cancelsCallTestWhenOneOfTheThreeIsAbsent() {
        Instant createdAt = Instant.parse("2026-10-29T10:00:00Z");
        DatingSession session = DatingSession.createCallTest(createdAt.plusSeconds(180), 1L, 2L, 3L, createdAt);
        var participants = List.of(
                SessionParticipant.apply(8L, 1L, Gender.MALE, "host", createdAt),
                SessionParticipant.apply(8L, 2L, Gender.FEMALE, "first", createdAt),
                SessionParticipant.apply(8L, 3L, Gender.FEMALE, "second", createdAt));
        var sessionRepository = mock(DatingSessionRepository.class);
        var participantRepository = mock(SessionParticipantRepository.class);
        when(sessionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(session));
        when(participantRepository.findAllBySessionIdAndStatus(8L, ParticipantStatus.APPLIED))
                .thenReturn(participants);
        SessionLiveService service = new SessionLiveService(sessionRepository, participantRepository,
                mock(GemWallet.class), Clock.fixed(session.lobbyCloseAt(), ZoneOffset.UTC));

        AttendanceResult result = service.finalizeAttendance(8L, Set.of(1L, 2L));

        assertThat(result.proceed()).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(participants.get(0).getStatus()).isEqualTo(ParticipantStatus.ATTENDED);
        assertThat(participants.get(1).getStatus()).isEqualTo(ParticipantStatus.ATTENDED);
        assertThat(participants.get(2).getStatus()).isEqualTo(ParticipantStatus.NO_SHOW);
    }
}
