package com.example.kiwi_backend.auth.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * 가입 전 학교 이메일 인증 코드.
 * 코드를 보낼 때마다 새 행이 생기고, 확인은 해당 이메일의 가장 최근 행으로만 한다.
 * 코드는 해시로 저장한다 (DB가 유출돼도 진행 중인 인증 코드가 바로 보이지 않게).
 */
@Entity
@Table(name = "email_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    public static final int MAX_ATTEMPTS = 5;

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인증 대상 학교 이메일 */
    @Column(nullable = false, updatable = false)
    private String email;

    /** 인증 코드, 해시 저장 */
    @Column(nullable = false, updatable = false)
    private String codeHash;

    /** 무차별 대입 방지, MAX_ATTEMPTS 이상이면 이 코드는 더 이상 확인 불가 */
    @Column(nullable = false)
    private int attemptCount;

    /** 코드 만료 시각 */
    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    /** 인증 성공 시각 */
    private Instant verifiedAt;

    /** 코드 발송 시각 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static EmailVerification issue(String email, String codeHash, Instant now, Duration ttl) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.codeHash = codeHash;
        verification.attemptCount = 0;
        verification.expiresAt = now.plus(ttl);
        return verification;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean hasAttemptsLeft() {
        return attemptCount < MAX_ATTEMPTS;
    }

    public int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attemptCount);
    }

    /** 틀린 코드를 입력했을 때 */
    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    public void markVerified(Instant now) {
        if (isVerified()) {
            throw new IllegalStateException("이미 사용된 인증 코드입니다");
        }
        this.verifiedAt = now;
    }
}
