package com.example.kiwi_backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 발급과 검증. 두 종류의 토큰을 만든다.
 *
 * - 로그인 토큰(access): 회원 id와 권한을 담고, 모든 인증 API와 WebSocket 연결에 쓴다
 * - 가입 토큰(signup): 이메일 인증을 마친 이메일을 담고, 가입 API에서만 쓴다
 *
 * 같은 키로 서명하므로 "typ" 클레임으로 용도를 구분한다.
 * 토큰 내용은 누구나 읽을 수 있으므로 개인정보는 넣지 않는다.
 */
@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_SIGNUP = "signup";
    private static final int MIN_KEY_BYTES = 32;   // HS256은 최소 256비트

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration signupTtl;
    private final Clock clock;

    /** Autowired: Spring이 쓰는 생성자 */
    @Autowired
    public JwtProvider(@Value("${app.jwt.secret}") String base64Secret,
                       @Value("${app.jwt.access-ttl:7d}") Duration accessTtl,
                       @Value("${app.jwt.signup-ttl:30m}") Duration signupTtl) {
        this(base64Secret, accessTtl, signupTtl, Clock.systemUTC());
    }

    /** 테스트에서 시간을 고정하기 위한 생성자 */
    JwtProvider(String base64Secret, Duration accessTtl, Duration signupTtl, Clock clock) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 은 32바이트 이상이어야 합니다 (openssl rand -base64 32 로 생성)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = accessTtl;
        this.signupTtl = signupTtl;
        this.clock = clock;
    }

    // ===== 발급 =====

    /** 로그인 토큰. role은 "MEMBER" 또는 "ADMIN" */
    public String createAccessToken(Long memberId, String role) {
        if (memberId == null || role == null) {
            throw new IllegalArgumentException("회원 id와 권한은 필수입니다");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** 이메일 인증 직후 가입 화면에서만 쓰는 짧은 토큰 */
    public String createSignupToken(String verifiedEmail) {
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(verifiedEmail)
                .claim(CLAIM_TYPE, TYPE_SIGNUP)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(signupTtl)))
                .signWith(key)
                .compact();
    }

    // ===== 검증 =====

    /** 로그인 토큰을 검증하고 사용자 정보를 꺼낸다 */
    public AuthUser parseAccessToken(String token) {
        Claims claims = parse(token, TYPE_ACCESS);
        try {
            Long memberId = Long.valueOf(claims.getSubject());
            String role = claims.get(CLAIM_ROLE, String.class);
            if (role == null) {
                throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "권한 정보가 없는 토큰입니다");
            }
            return new AuthUser(memberId, role);
        } catch (NumberFormatException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "잘못된 토큰입니다", e);
        }
    }

    /** 가입 토큰을 검증하고 인증된 이메일을 꺼낸다 */
    public String parseSignupToken(String token) {
        return parse(token, TYPE_SIGNUP).getSubject();
    }

    private Claims parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "토큰이 없습니다");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)                              // 서명 검증
                    .clock(() -> Date.from(clock.instant()))      // 만료 판단 기준 시각
                    .build()
                    .parseSignedClaims(token)                     // 위조·만료면 여기서 예외
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED, "만료된 토큰입니다", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "잘못된 토큰입니다", e);
        }

        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "용도가 다른 토큰입니다");
        }
        return claims;
    }
}
