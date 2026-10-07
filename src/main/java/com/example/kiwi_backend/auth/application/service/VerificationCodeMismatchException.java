package com.example.kiwi_backend.auth.application.service;

/**
 * 인증 코드가 틀렸을 때.
 * IllegalArgumentException을 상속해서 공통 예외 처리기에서 400으로 응답된다. (GlobalExceptionHandler에 등록 필요 X)
 * 별도 클래스로 둔 이유는 이 예외가 나도 트랜잭션을 롤백하지 않기 위해서다 (EmailVerificationService 참고).
 */
public class VerificationCodeMismatchException extends IllegalArgumentException {

    public VerificationCodeMismatchException(String message) {
        super(message);
    }
}
