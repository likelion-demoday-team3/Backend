package com.example.kiwi_backend.auth.api.dto;

/** 이메일 인증 성공 시 발급. 가입 요청에 그대로 담아 보낸다 */
public record SignupTokenResponse(String signupToken) {
}
