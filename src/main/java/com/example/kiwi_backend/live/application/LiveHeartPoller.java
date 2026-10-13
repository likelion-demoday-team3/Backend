package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveHeartPoller {
    private final SessionLiveAccess access;
    private final LiveVotingStartService voting;
    private final Clock clock;

    @Scheduled(fixedDelay = 1_000)
    public void poll() {
        for (Long sessionId : access.findHeartSessionsDue(clock.instant())) {
            try {
                voting.startVoting(sessionId, clock.instant());
            } catch (RuntimeException e) {
                log.error("Heart deadline transition failed: sessionId={}", sessionId, e);
            }
        }
    }
}
