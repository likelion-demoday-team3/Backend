package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLiveStatusCommand;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionPoller {

    private final SessionQueryService sessionQueryService;
    private final SessionLiveStatusCommand liveStatusCommand;
    private final LiveSessionStartService liveSessionStartService;
    private final LiveRoundSchedulerService liveRoundSchedulerService;
    private final Clock clock;

    @Scheduled(fixedDelay = 5_000)
    public void poll() {
        Instant now = clock.instant();

        // ① 로비 마감
        // 로비를 마감해야 할 세션을 조회하고, 해당 세션에 대해 라이브 세션을 시작한다
        for (Long sessionId : sessionQueryService.findSessionIdsDueForLobbyClose(now)) {
            try {
                liveSessionStartService.startLiveSession(sessionId);
            } catch (RuntimeException e) {
                log.error("로비 마감 실패 sessionId={}", sessionId, e);   // 5초 뒤 다시 시도됨
            }
        }

        // ② 1라운드 시작
        // 1라운드를 시작해야 할 세션을 조회하고, 해당 세션에 대해 1라운드를 시작한다
        for (Long sessionId : sessionQueryService.findSessionIdsDueForFirstRound(now)) {
            try {
                liveRoundSchedulerService.startSessionRounds(sessionId);
                liveStatusCommand.markRoundsStarted(sessionId);
            } catch (RuntimeException e) {
                log.error("1라운드 시작 실패 sessionId={}", sessionId, e);
            }
        }
    }
}
