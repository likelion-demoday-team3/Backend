package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.contract.command.MemberRegistrar;
import com.example.kiwi_backend.contract.dto.RegisterMemberCommand;
import com.example.kiwi_backend.contract.event.MemberRegisteredEvent;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.entity.Profile;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import com.example.kiwi_backend.member.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * auth 모듈이 이메일 인증을 마친 뒤 호출한다.
 * 회원, 빈 프로필, (이벤트를 받은 wallet의) 지갑이 한 트랜잭션에서 함께 만들어진다.
 */
@Service
@RequiredArgsConstructor
public class MemberRegistrarImpl implements MemberRegistrar {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Long register(RegisterMemberCommand command) {
        String email = command.email().strip().toLowerCase();
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 가입된 이메일입니다");
        }

        Member member = Member.register(
                email,
                command.name(),
                command.passwordHash(),
                command.university(),
                command.gender(),
                command.birthDate(),
                command.phoneNumber(),
                command.instagramId(),
                command.emailVerifiedAt(),
                LocalDate.now(KOREA)
        );
        memberRepository.save(member);
        profileRepository.save(Profile.create(member));

        eventPublisher.publishEvent(new MemberRegisteredEvent(member.getId()));
        return member.getId();
    }
}

