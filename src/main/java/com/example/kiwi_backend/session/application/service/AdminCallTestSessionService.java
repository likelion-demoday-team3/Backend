package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import com.example.kiwi_backend.session.api.dto.CallTestSessionRequest;
import com.example.kiwi_backend.session.api.dto.CallTestSessionResponse;
import com.example.kiwi_backend.session.application.support.AliasGenerator;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminCallTestSessionService {

    private final MemberRepository memberRepository;
    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final AliasGenerator aliasGenerator;
    private final Clock clock;

    @Transactional
    public CallTestSessionResponse create(CallTestSessionRequest request) {
        Instant now = clock.instant();
        Member host = findActiveMember(request.hostEmail());
        Member firstPartner = findActiveMember(request.firstPartnerEmail());
        Member secondPartner = findActiveMember(request.secondPartnerEmail());
        boolean fivePeople = request.secondMaleEmail() != null && request.thirdFemaleEmail() != null;
        if ((request.secondMaleEmail() == null) != (request.thirdFemaleEmail() == null)) {
            throw new IllegalArgumentException("5인 테스트에는 남성 2명과 여성 3명이 필요합니다");
        }
        Member secondMale = fivePeople ? findActiveMember(request.secondMaleEmail()) : null;
        Member thirdFemale = fivePeople ? findActiveMember(request.thirdFemaleEmail()) : null;
        List<Long> memberIds = fivePeople
                ? List.of(host.getId(), firstPartner.getId(), secondPartner.getId(), secondMale.getId(), thirdFemale.getId())
                : List.of(host.getId(), firstPartner.getId(), secondPartner.getId());
        if (new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new IllegalArgumentException("서로 다른 활성 회원을 입력해 주세요");
        }

        DatingSession session = sessionRepository.saveAndFlush(DatingSession.createCallTest(
                now.plus(Duration.ofMinutes(5)), host.getId(), firstPartner.getId(), secondPartner.getId(), now));

        Set<String> aliases = new HashSet<>();
        List<SessionParticipant> participants = new java.util.ArrayList<>();
        participants.add(participant(session, host, Gender.MALE, aliases, now));
        participants.add(participant(session, firstPartner, Gender.FEMALE, aliases, now));
        participants.add(participant(session, secondPartner, Gender.FEMALE, aliases, now));
        if (fivePeople) {
            participants.add(participant(session, secondMale, Gender.MALE, aliases, now));
            participants.add(participant(session, thirdFemale, Gender.FEMALE, aliases, now));
        }
        participantRepository.saveAll(participants);

        return new CallTestSessionResponse(session.getId(), session.getStartTime(),
                session.lobbyCloseAt(), session.firstRoundAt());
    }

    private Member findActiveMember(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmail(normalized)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다: " + normalized));
        if (!member.isActive()) {
            throw new IllegalStateException("활성 회원만 통화 테스트에 추가할 수 있습니다");
        }
        return member;
    }

    private SessionParticipant participant(DatingSession session,
                                            Member member,
                                            Gender testGender,
                                            Set<String> aliases,
                                            Instant now) {
        String alias = aliasGenerator.generate(aliases);
        aliases.add(alias);
        return SessionParticipant.apply(session.getId(), member.getId(), testGender, alias, now);
    }
}
