package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.common.realtime.UserMessageEvent;
import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.dto.LiveSessionContext.Phase;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LiveHeartPhaseService {
    private final SessionLiveAccess access;
    private final LiveRoundRepository rounds;
    private final ApplicationEventPublisher events;

    @Transactional
    public void startHearts(Long sessionId, Instant now) {
        var context = access.lock(sessionId);
        if (context.phase() == Phase.HEART) return;
        var allRounds = rounds.findAllBySessionIdOrderByRoundNumberAsc(sessionId);
        if (context.phase() != Phase.IN_ROUND || allRounds.isEmpty()
                || allRounds.stream().anyMatch(r -> r.getStatus() != LiveRoundStatus.COMPLETED)) {
            throw new IllegalStateException("All rounds must be completed before hearts");
        }
        Instant endsAt = access.startHearts(sessionId, now);
        for (var participant : context.participants()) {
            events.publishEvent(new UserMessageEvent(participant.memberId(), "/queue/live/hearts-started",
                    new HeartsStarted(sessionId, endsAt)));
        }
    }

    public record HeartsStarted(Long sessionId, Instant heartsEndsAt) { }
}
