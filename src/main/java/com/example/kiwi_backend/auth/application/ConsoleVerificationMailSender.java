package com.example.kiwi_backend.auth.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발용. 메일 대신 콘솔에 인증 코드를 찍는다.
 *
 * prod 프로필에서는 등록되지 않는다.
 * 그래서 실제 발송 구현체 없이 운영 서버를 띄우면 빈이 없어서 바로 실패한다 (실수로 콘솔 발송이 운영에 나가는 것 방지).
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class ConsoleVerificationMailSender implements VerificationMailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("[개발용 인증 메일] to={} code={}", email, code);
    }
}
