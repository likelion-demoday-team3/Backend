package com.example.kiwi_backend.common.exception;

/**
 * 결제사에 요청은 보냈지만 응답을 받지 못함 (연결 끊김, 응답 시간 초과, 응답 해석 실패).
 * 결제사 쪽에서는 이미 처리됐을 수 있으므로, 호출한 쪽이 결제 상태를 조회해 확인해야 한다.
 * 사용자에게는 PaymentGatewayException과 똑같이 502로 보인다.
 */
public class PaymentResultUnknownException extends PaymentGatewayException {

    public PaymentResultUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
