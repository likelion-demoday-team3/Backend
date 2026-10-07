package com.example.kiwi_backend.contract.command;

import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.GemResult;

/**
 * 보석 변동의 유일한 창구. session, 결제 등은 wallet을 직접 import하지 않고 이것만 쓴다.
 *
 * 호출한 쪽의 트랜잭션에 함께 묶인다. 예를 들어 세션 신청에서 참가비 차감 후 다른 처리가 실패하면 차감도 취소된다.
 * 잔액이 부족하면 InsufficientGemException을 던진다.
 */
public interface GemWallet {

    GemResult apply(GemCommand command);
}
