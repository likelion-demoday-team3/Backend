package com.example.kiwi_backend.wallet.application.service;

import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.dto.GemCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 이벤트 기간 가입 축하 보석.
 *
 * 가입 트랜잭션 안(MemberRegisteredListener)에서 불린다. 지급이 실패하면 가입도 함께 취소되고,
 * 멱등 키가 회원당 하나라 두 번 불려도 한 번만 지급된다.
 * 기간·개수는 app.signup-bonus 설정(SIGNUP_BONUS_UNTIL, SIGNUP_BONUS_GEM)으로 바꾼다.
 */
@Service
public class SignupBonusService {

    static final String MEMO = "가입 축하 보석";
    static final String LINK = "/store/history";

    private final WalletService walletService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int gem;
    /** 이 시각까지(포함) 가입하면 지급. null이면 지급하지 않음 */
    private final Instant until;

    public SignupBonusService(WalletService walletService,
                              ApplicationEventPublisher events,
                              Clock clock,
                              @Value("${app.signup-bonus.gem:0}") int gem,
                              @Value("${app.signup-bonus.until:}") String until) {
        this.walletService = walletService;
        this.events = events;
        this.clock = clock;
        this.gem = gem;
        // 예: 2026-11-10T23:59:59+09:00 (시간대를 꼭 붙인다)
        this.until = until == null || until.isBlank() ? null : OffsetDateTime.parse(until.trim()).toInstant();
    }

    public void grantIfEligible(Long memberId) {
        if (gem <= 0 || until == null || clock.instant().isAfter(until)) {
            return;
        }
        walletService.apply(GemCommand.signupBonus(memberId, gem, MEMO));
        events.publishEvent(new NotificationEvent(memberId, NotificationType.SIGNUP_BONUS,
                "가입 축하 보석이 도착했어요",
                "가입을 축하해요! 보석 ◆" + gem + "개를 드렸어요.",
                null, LINK, "SIGNUP_BONUS:m" + memberId));
    }
}
