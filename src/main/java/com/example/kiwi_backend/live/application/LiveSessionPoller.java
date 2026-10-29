package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.query.SessionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionPoller {
    private final SessionQueryService sessionQueryService;
    private final LiveSessionStartService sessionStartService;
    private final LiveRoundSchedulerService roundSchedulerService;
    private final CompatibilityPreparationService compatibilityPreparationService;
    private final Clock clock;

    @Scheduled(fixedDelay = 5_000)
    public void poll() {
        for (Long sessionId : sessionQueryService.findSessionIdsDueForLobbyClose(clock.instant())) {
            try {
                sessionStartService.startLiveSession(sessionId);
            } catch (RuntimeException e) {
                log.error("Lobby close failed: sessionId={}", sessionId, e);
            }
        }
        for (Long sessionId : sessionQueryService.findSessionIdsDueForFirstRound(clock.instant())) {
            try {
                if (sessionQueryService.isCallTestSession(sessionId)) {
                    roundSchedulerService.startSessionRounds(sessionId);
                } else if (compatibilityPreparationService.isPrepared(sessionId)) {
                    roundSchedulerService.startSessionRounds(sessionId);
                } else {
                    compatibilityPreparationService.requestPreparation(sessionId);
                }
            } catch (RuntimeException e) {
                log.error("First round start failed: sessionId={}", sessionId, e);
            }
        }
    }
}
