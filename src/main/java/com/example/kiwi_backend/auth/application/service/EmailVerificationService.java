package com.example.kiwi_backend.auth.application.service;

import com.example.kiwi_backend.auth.application.UniversityDomainPolicy;
import com.example.kiwi_backend.auth.application.VerificationMailSender;
import com.example.kiwi_backend.auth.domain.entity.EmailVerification;
import com.example.kiwi_backend.auth.domain.repository.EmailVerificationRepository;
import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.contract.query.MemberCredentialQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * 학교 이메일 인증: 코드 발송 → 코드 확인 → 가입 토큰 발급.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository verificationRepository;
    private final UniversityDomainPolicy universityDomainPolicy;
    private final VerificationMailSender mailSender;
    private final MemberCredentialQuery memberCredentialQuery;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** 인증 코드를 만들어 저장하고 메일로 보낸다 */
    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        universityDomainPolicy.universityOf(email);   // 허용 학교가 아니면 여기서 예외

        if (memberCredentialQuery.findByEmail(email).isPresent()) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }

        Instant now = Instant.now();
        verificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .filter(latest -> latest.getCreatedAt().plus(RESEND_INTERVAL).isAfter(now))
                .ifPresent(latest -> {
                    throw new IllegalStateException("잠시 후 다시 요청해주세요.");
                });

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        verificationRepository.save(
                EmailVerification.issue(email, passwordEncoder.encode(code), now, CODE_TTL));

        mailSender.sendVerificationCode(email, code);
    }

    /**
     * 코드를 확인하고 가입 토큰을 발급한다.
     *
     * noRollbackFor: 코드가 틀리면 시도 횟수를 올린 뒤 예외를 던지는데,
     * 기본 동작대로 롤백되면 올린 시도 횟수까지 취소돼서 무제한으로 맞혀볼 수 있게 된다.
     * 그래서 틀린 코드 예외일 때만 롤백하지 않고 시도 횟수를 저장한다.
     */
    @Transactional(noRollbackFor = VerificationCodeMismatchException.class)
    public String verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        EmailVerification verification = verificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("인증 코드를 먼저 요청해주세요."));

        Instant now = Instant.now();
        if (verification.isVerified()) {
            throw new IllegalStateException("이미 사용된 인증 코드입니다. 새 코드를 요청해주세요.");
        }
        if (verification.isExpired(now)) {
            throw new IllegalStateException("인증 코드가 만료됐어요. 새 코드를 요청해주세요.");
        }
        if (!verification.hasAttemptsLeft()) {
            throw new IllegalStateException("시도 횟수를 초과했어요. 새 코드를 요청해주세요.");
        }

        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            verification.recordFailedAttempt();
            throw new VerificationCodeMismatchException(
                    "인증 코드가 올바르지 않습니다. (남은 횟수 " + verification.remainingAttempts() + "회)");
        }

        verification.markVerified(now);
        return jwtProvider.createSignupToken(email);
    }

    private static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        return email.strip().toLowerCase();
    }
}
