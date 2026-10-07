package com.example.kiwi_backend.auth.api.controller;

import com.example.kiwi_backend.auth.api.dto.EmailCodeRequest;
import com.example.kiwi_backend.auth.api.dto.EmailVerifyRequest;
import com.example.kiwi_backend.auth.api.dto.LoginRequest;
import com.example.kiwi_backend.auth.api.dto.SignupRequest;
import com.example.kiwi_backend.auth.api.dto.SignupTokenResponse;
import com.example.kiwi_backend.auth.api.dto.TokenResponse;
import com.example.kiwi_backend.auth.application.service.EmailVerificationService;
import com.example.kiwi_backend.auth.application.service.LoginService;
import com.example.kiwi_backend.auth.application.service.SignupService;
import com.example.kiwi_backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. SecurityConfig에서 /auth/** 는 로그인 없이 호출 가능.
 *
 * 가입 순서: /email/code → /email/verify → /signup
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final EmailVerificationService emailVerificationService;
    private final SignupService signupService;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(loginService.login(request.email(), request.password()));
    }

    /** 1. 학교 이메일로 인증 코드 발송 */
    @PostMapping("/email/code")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        emailVerificationService.sendCode(request.email());
        return ApiResponse.ok();
    }

    /** 2. 인증 코드 확인 → 가입 토큰 발급 */
    @PostMapping("/email/verify")
    public ApiResponse<SignupTokenResponse> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        String signupToken = emailVerificationService.verify(request.email(), request.code());
        return ApiResponse.ok(new SignupTokenResponse(signupToken));
    }

    /** 3. 가입 → 로그인 토큰 발급 */
    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(signupService.signup(request));
    }
}
