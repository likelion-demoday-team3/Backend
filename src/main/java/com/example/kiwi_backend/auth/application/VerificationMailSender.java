package com.example.kiwi_backend.auth.application;

/**
 * 인증 코드 메일 발송.
 * 로컬에서는 ConsoleVerificationMailSender가 콘솔에 코드를 찍고,
 * 운영에서는 Resend나 SES 같은 실제 발송 구현체를 만들어 등록한다.
 */
public interface VerificationMailSender {

    void sendVerificationCode(String email, String code);
}
