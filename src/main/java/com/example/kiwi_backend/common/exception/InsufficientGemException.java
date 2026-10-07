package com.example.kiwi_backend.common.exception;

/**
 * 보석 잔액 부족.
 * 프론트가 "충전하기" 안내를 띄울 수 있게 별도 에러 코드(INSUFFICIENT_GEM)로 응답하는 것을 권장한다.
 * session 등 다른 모듈에서도 이 예외를 볼 수 있도록 common에 둔다.
 */
public class InsufficientGemException extends IllegalStateException {

    private final int balance;
    private final int required;

    public InsufficientGemException(int balance, int required) {
        super("보석이 부족합니다 (보유 " + balance + "개, 필요 " + required + "개)");
        this.balance = balance;
        this.required = required;
    }

    public int getBalance() {
        return balance;
    }

    public int getRequired() {
        return required;
    }
}
