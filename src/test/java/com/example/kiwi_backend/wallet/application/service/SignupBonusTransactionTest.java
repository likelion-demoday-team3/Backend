package com.example.kiwi_backend.wallet.application.service;

import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.command.MemberRegistrar;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.GemTransactionType;
import com.example.kiwi_backend.contract.dto.RegisterMemberCommand;
import com.example.kiwi_backend.notification.application.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 가입하면(회원 등록 → 지갑 생성) 기간 안이면 가입 축하 보석이 실제로 저장되고 알림이 남는다 */
@SpringBootTest(properties = {
        "app.signup-bonus.gem=15",
        "app.signup-bonus.until=2999-12-31T23:59:59+09:00"
})
@ActiveProfiles("test")
class SignupBonusTransactionTest {

    @Autowired MemberRegistrar memberRegistrar;
    @Autowired TransactionTemplate tx;
    @Autowired WalletService wallets;
    @Autowired SignupBonusService signupBonus;
    @Autowired NotificationService notifications;

    @Test
    void newMemberGetsBonusOnceWithNotification() {
        String email = "bonus-" + UUID.randomUUID().toString().substring(0, 8) + "@yonsei.ac.kr";
        Long memberId = tx.execute(status -> memberRegistrar.register(new RegisterMemberCommand(
                email, "가입보너스", "hash", "연세대학교", Gender.FEMALE, LocalDate.of(2003, 1, 1),
                null, "bonus_test", Instant.now(), "서울", Instant.now(), "2026-10-31")));
        // 다시 불려도 한 번만 지급된다
        tx.executeWithoutResult(status -> signupBonus.grantIfEligible(memberId));

        assertThat(wallets.getBalance(memberId).gemBalance()).isEqualTo(15);
        var history = wallets.getHistory(memberId, 0, 10);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().type()).isEqualTo(GemTransactionType.SIGNUP_BONUS);
        assertThat(history.getFirst().description()).isEqualTo("가입 축하 보석");
        assertThat(history.getFirst().amount()).isEqualTo(15);

        var items = notifications.list(memberId, 0, 10).items();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().type()).isEqualTo(NotificationType.SIGNUP_BONUS);
        assertThat(items.getFirst().link()).isEqualTo("/store/history");
    }
}
