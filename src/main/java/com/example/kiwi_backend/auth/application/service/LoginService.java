package com.example.kiwi_backend.auth.application.service;

import com.example.kiwi_backend.auth.api.dto.TokenResponse;
import com.example.kiwi_backend.common.exception.AuthenticationFailedException;
import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.contract.dto.MemberCredential;
import com.example.kiwi_backend.contract.query.MemberCredentialQuery;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LoginService {

    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다";

    private final MemberCredentialQuery memberCredentialQuery;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** 없는 이메일일 때도 비밀번호 비교를 한 번 해서, 응답 시간으로 가입 여부를 추측하지 못하게 한다 */
    private final String dummyHash;

    public LoginService(MemberCredentialQuery memberCredentialQuery,
                        PasswordEncoder passwordEncoder,
                        JwtProvider jwtProvider) {
        this.memberCredentialQuery = memberCredentialQuery;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public TokenResponse login(String email, String rawPassword) {
        Optional<MemberCredential> found = memberCredentialQuery.findByEmail(email);

        String hash = found.map(MemberCredential::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hash);

        MemberCredential credential = found
                .filter(c -> passwordMatches)
                .orElseThrow(() -> new AuthenticationFailedException(LOGIN_FAILED_MESSAGE));

        if (!credential.active()) {
            throw new AuthenticationFailedException("이용할 수 없는 계정입니다");
        }

        String accessToken = jwtProvider.createAccessToken(credential.memberId(), credential.role());
        return TokenResponse.bearer(accessToken);
    }
}
