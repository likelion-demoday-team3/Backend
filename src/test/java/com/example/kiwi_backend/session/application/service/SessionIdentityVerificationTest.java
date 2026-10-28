package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.MemberSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.session.application.support.AliasGenerator;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionIdentityVerificationTest {

    @Test
    void allowsSessionApplicationWithoutIdentityVerification() {
        var sessions = mock(DatingSessionRepository.class);
        var participants = mock(SessionParticipantRepository.class);
        var members = mock(MemberQueryService.class);
        var wallet = mock(GemWallet.class);
        var aliases = mock(AliasGenerator.class);
        var service = new SessionApplicationService(sessions, participants, members, wallet, aliases,
                Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC));
        when(members.getSummary(1L)).thenReturn(new MemberSummary(1L, Gender.FEMALE, true, false));
        when(sessions.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        // 본인인증 없이도 세션 조회 단계까지 진행된다
        assertThatThrownBy(() -> service.apply(1L, 2L))
                .isInstanceOf(NotFoundException.class);

        verify(sessions).findByIdForUpdate(2L);
    }
}
