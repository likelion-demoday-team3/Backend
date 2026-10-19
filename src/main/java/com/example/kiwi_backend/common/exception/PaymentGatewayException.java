package com.example.kiwi_backend.common.exception;

/**
 * 외부 결제사(토스페이먼츠) 호출 실패. 502로 변환된다.
 * 메시지는 사용자에게 그대로 보이므로 결제사 원문 응답은 넣지 않고 로그로만 남긴다.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
