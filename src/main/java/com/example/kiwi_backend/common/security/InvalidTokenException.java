package com.example.kiwi_backend.common.security;

/**
 * 토큰 검증 실패. 라이브러리(jjwt) 예외를 감싸서, 필터와 WebSocket 인증이 라이브러리를 몰라도 되게 한다.
 * 만료와 그 외를 구분해서 프론트가 "다시 로그인" 안내를 할 수 있게 한다.
 */
public class InvalidTokenException extends RuntimeException{
    public enum Reason {
        EXPIRED,   // 만료됨 → 다시 로그인
        INVALID    // 위조, 형식 오류, 용도가 다른 토큰
    }
    private final Reason reason;

    public InvalidTokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public InvalidTokenException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
}
