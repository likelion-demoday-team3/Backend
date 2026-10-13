package com.example.kiwi_backend.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !test")
@RequiredArgsConstructor
public class SmtpVerificationMailSender implements VerificationMailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String from;

    @Override
    public void sendVerificationCode(String email, String code) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("app.mail.from must be configured for SMTP delivery");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Kiwi 학교 이메일 인증번호");
        message.setText("인증번호는 " + code + "입니다. 5분 안에 입력해주세요.");
        mailSender.send(message);
    }
}
