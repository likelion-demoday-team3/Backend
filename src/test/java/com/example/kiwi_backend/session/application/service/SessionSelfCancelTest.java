package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.session.application.support.AliasGenerator;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionSelfCancelTest {

    private final Instant start = Instant.parse("2030-01-01T12:00:00Z");
    private final DatingSessionRepository sessions = mock(DatingSessionRepository.class);
    private final SessionParticipantRepository participants = mock(SessionParticipantRepository.class);
    private final GemWallet wallet = mock(GemWallet.class);

    private SessionApplicationService serviceAt(Instant now) {
        return new SessionApplicationService(sessions, participants, mock(MemberQueryService.class), wallet,
                mock(AliasGenerator.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    private SessionParticipant applied(Instant appliedAt) {
        var participant = SessionParticipant.apply(1L, 7L, Gender.FEMALE, "졸린 수달", appliedAt);
        when(participants.findBySessionIdAndMemberId(1L, 7L)).thenReturn(Optional.of(participant));
        return participant;
    }

    @Test
    void readySessionCanBeCancelledWithoutRefund() {
        var session = DatingSession.create(start, 3, 180, 120, 300, 12, 2, start.minusSeconds(7200));
        session.openRecruiting(start.minusSeconds(7200));
        session.markReady();
        when(sessions.findByIdForUpdate(1L)).thenReturn(Optional.of(session));
        var participant = applied(start.minusSeconds(7000));

        var result = serviceAt(start.minusSeconds(600)).cancel(7L, 1L);

        assertThat(result.refundedGem()).isZero();
        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        verifyNoInteractions(wallet);
    }

    @Test
    void cancelledSessionCannotBeSelfCancelled() {
        var session = DatingSession.create(start, 3, 180, 120, 300, 12, 2, start.minusSeconds(7200));
        session.openRecruiting(start.minusSeconds(7200));
        session.cancel();
        when(sessions.findByIdForUpdate(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> serviceAt(start.minusSeconds(600)).cancel(7L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("취소할 수 있는 기간이 지났습니다");
    }
}
