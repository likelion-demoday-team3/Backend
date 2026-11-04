package com.example.kiwi_backend.wallet.application.service;

import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.GemTransactionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 가입 축하 보석: 마감 시각(포함)까지 가입하면 15개 + 알림, 지나면 아무것도 하지 않는다 */
class SignupBonusServiceTest {

    private static final String UNTIL = "2026-11-10T23:59:59+09:00";
    private static final Instant UNTIL_INSTANT = Instant.parse("2026-11-10T14:59:59Z");

    private final WalletService wallet = mock(WalletService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private SignupBonusService service(Instant now, int gem, String until) {
        return new SignupBonusService(wallet, events, Clock.fixed(now, ZoneOffset.UTC), gem, until);
    }

    @Test
    void grantsGemsAndNotifiesWithinPeriod() {
        service(UNTIL_INSTANT.minusSeconds(3600), 15, UNTIL).grantIfEligible(7L);

        ArgumentCaptor<GemCommand> command = ArgumentCaptor.forClass(GemCommand.class);
        verify(wallet).apply(command.capture());
        assertThat(command.getValue().memberId()).isEqualTo(7L);
        assertThat(command.getValue().type()).isEqualTo(GemTransactionType.SIGNUP_BONUS);
        assertThat(command.getValue().amount()).isEqualTo(15);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("SIGNUP_BONUS:m7");

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().memberId()).isEqualTo(7L);
        assertThat(event.getValue().type()).isEqualTo(NotificationType.SIGNUP_BONUS);
        assertThat(event.getValue().body()).contains("15개");
        assertThat(event.getValue().dedupeKey()).isEqualTo("SIGNUP_BONUS:m7");
    }

    @Test
    void deadlineItselfIsIncluded() {
        service(UNTIL_INSTANT, 15, UNTIL).grantIfEligible(7L);

        verify(wallet).apply(any());
    }

    @Test
    void nothingAfterDeadline() {
        service(UNTIL_INSTANT.plusSeconds(1), 15, UNTIL).grantIfEligible(7L);

        verifyNoInteractions(wallet, events);
    }

    @Test
    void disabledWhenUntilBlankOrGemZero() {
        Instant now = UNTIL_INSTANT.minusSeconds(3600);
        service(now, 15, "").grantIfEligible(7L);
        service(now, 0, UNTIL).grantIfEligible(7L);

        verifyNoInteractions(wallet, events);
    }
}
