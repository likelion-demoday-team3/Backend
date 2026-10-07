package com.example.kiwi_backend.auth.application.service;

import com.example.kiwi_backend.auth.api.dto.SignupRequest;
import com.example.kiwi_backend.auth.api.dto.TokenResponse;
import com.example.kiwi_backend.auth.application.UniversityDomainPolicy;
import com.example.kiwi_backend.auth.domain.entity.EmailVerification;
import com.example.kiwi_backend.auth.domain.repository.EmailVerificationRepository;
import com.example.kiwi_backend.common.exception.AuthenticationFailedException;
import com.example.kiwi_backend.common.security.InvalidTokenException;
import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.contract.command.MemberRegistrar;
import com.example.kiwi_backend.contract.dto.RegisterMemberCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 가입: 가입 토큰으로 이메일 인증을 확인하고, member 모듈에 회원 생성을 요청한 뒤 로그인 토큰을 발급한다.
 * 회원·프로필·지갑 생성이 이 트랜잭션 하나로 묶인다 (MemberRegisteredEvent를 wallet이 같은 트랜잭션에서 처리).
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String NEW_MEMBER_ROLE = "MEMBER";

    private final JwtProvider jwtProvider;
    private final UniversityDomainPolicy universityDomainPolicy;
    private final EmailVerificationRepository verificationRepository;
    private final MemberRegistrar memberRegistrar;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = emailFromSignupToken(request.signupToken());
        String university = universityDomainPolicy.universityOf(email);

        Instant verifiedAt = verificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .map(EmailVerification::getVerifiedAt)
                .orElse(Instant.now());

        Long memberId = memberRegistrar.register(new RegisterMemberCommand(
                email,
                passwordEncoder.encode(request.password()),
                university,
                request.gender(),
                request.birthDate(),
                request.phoneNumber(),
                request.instagramId(),
                verifiedAt
        ));

        return TokenResponse.bearer(jwtProvider.createAccessToken(memberId, NEW_MEMBER_ROLE));
    }

    /** 가입 토큰 검증 실패는 500이 아니라 다시 인증하라는 401로 응답한다 */
    private String emailFromSignupToken(String signupToken) {
        try {
            return jwtProvider.parseSignupToken(signupToken);
        } catch (InvalidTokenException e) {
            String message = e.getReason() == InvalidTokenException.Reason.EXPIRED
                    ? "가입 시간이 지났어요. 이메일 인증을 다시 해주세요"
                    : "이메일 인증 정보가 올바르지 않습니다. 이메일 인증을 다시 해주세요";
            throw new AuthenticationFailedException(message);
        }
    }
}
