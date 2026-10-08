package com.example.kiwi_backend.wallet.application.listener;

import com.example.kiwi_backend.contract.event.MemberRegisteredEvent;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 가입하면 지갑을 만든다.
 *
 * @TransactionalEventListener가 아니라 @EventListener를 쓰는 이유:
 * 가입 트랜잭션 안에서 바로 실행되어야, 지갑 생성이 실패하면 가입도 함께 취소된다.
 * (커밋 후에 실행하면 지갑 없는 회원이 남을 수 있다)
 */
@Component
@RequiredArgsConstructor
public class MemberRegisteredListener {

    private final WalletService walletService;

    @EventListener
    public void onMemberRegistered(MemberRegisteredEvent event) {
        walletService.createWallet(event.memberId());
    }
}
