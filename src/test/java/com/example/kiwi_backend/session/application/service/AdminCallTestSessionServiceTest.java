package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import com.example.kiwi_backend.session.api.dto.CallTestSessionRequest;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AdminCallTestSessionServiceTest {

    @Autowired
    private AdminCallTestSessionService service;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DatingSessionRepository sessionRepository;
    @Autowired
    private SessionParticipantRepository participantRepository;

    @Test
    @Transactional
    void createsReadyPrivateTestSessionWithTestOnlyGenderRolesAndRequestedPartners() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String hostEmail = "host." + suffix + "@sogang.ac.kr";
        String firstEmail = "first." + suffix + "@sogang.ac.kr";
        String secondEmail = "second." + suffix + "@sogang.ac.kr";
        Instant now = Instant.now();
        memberRepository.saveAllAndFlush(List.of(
                member(hostEmail, suffix + "a", now),
                member(firstEmail, suffix + "b", now),
                member(secondEmail, suffix + "c", now)));

        var response = service.create(new CallTestSessionRequest(hostEmail, firstEmail, secondEmail));

        DatingSession session = sessionRepository.findById(response.sessionId()).orElseThrow();
        var participants = participantRepository.findAllBySessionIdAndStatus(
                response.sessionId(), ParticipantStatus.APPLIED);
        assertThat(session.isCallTest()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(session.getEntryFeeGem()).isZero();
        assertThat(session.lobbyOpenAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(session.lobbyCloseAt()).isAfter(Instant.now());
        assertThat(response.firstRoundAt()).isEqualTo(session.firstRoundAt());
        assertThat(participants).hasSize(3);
        assertThat(participants).extracting(p -> p.getGender())
                .containsExactly(Gender.MALE, Gender.FEMALE, Gender.FEMALE);
        assertThat(session.getCallTestHostMemberId()).isEqualTo(participants.get(0).getMemberId());
        assertThat(session.getCallTestFirstPartnerMemberId()).isEqualTo(participants.get(1).getMemberId());
        assertThat(session.getCallTestSecondPartnerMemberId()).isEqualTo(participants.get(2).getMemberId());
    }

    private Member member(String email, String instagram, Instant now) {
        return Member.register(email, "테스트 사용자", "test-password-hash", "서강대학교",
                Gender.FEMALE, LocalDate.of(2002, 1, 1), null, instagram,
                now, LocalDate.now());
    }
}
