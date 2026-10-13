package com.example.kiwi_backend.vote.application;
import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
@Slf4j
@Component
@RequiredArgsConstructor
public class VoteDeadlinePoller {
    private final SessionLiveAccess access;
    private final VoteService votes;
    private final Clock clock;
    @Scheduled(fixedDelay = 1_000)
    public void poll() {
        for (Long id : access.findVotingSessionsDue(clock.instant())) {
            try { votes.close(id); }
            catch (RuntimeException e) { log.error("Vote closing failed: sessionId={}", id, e); }
        }
    }
}
