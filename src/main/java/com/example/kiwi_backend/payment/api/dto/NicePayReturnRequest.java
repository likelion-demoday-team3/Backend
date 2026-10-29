package com.example.kiwi_backend.payment.api.dto;

/**
 * 나이스페이 결제창이 returnUrl로 form POST 하는 인증 결과.
 * authResultCode가 "0000"일 때만 tid, signature가 온다. amount는 위변조 확인에 원문 그대로 쓰므로 문자열로 받는다
 */
public record NicePayReturnRequest(
        String authResultCode,
        String authResultMsg,
        String tid,
        String clientId,
        String orderId,
        String amount,
        String authToken,
        String signature
) {

    public boolean authenticated() {
        return "0000".equals(authResultCode);
    }
}
