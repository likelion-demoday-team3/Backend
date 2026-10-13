package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.common.realtime.UserMessageEvent;
import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.command.SessionLivePhaseCommand;
import com.example.kiwi_backend.contract.dto.LiveSessionContext.Phase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LiveVotingStartService {
    public static final String VOTING_STARTED_DESTINATION = "/queue/live/voting-started";
    private final SessionLiveAccess access;
    private final SessionLivePhaseCommand phaseCommand;
    private final ApplicationEventPublisher events;

    @Transactional
    public void startVoting(Long sessionId, Instant startedAt) {
        var context = access.lock(sessionId);
        if (context.phase() == Phase.VOTING) return;
        if (context.phase() != Phase.HEART || context.deadline() == null
                || startedAt.isBefore(context.deadline())) {
            throw new IllegalStateException("Heart deadline has not been reached");
        }
        Instant endsAt = phaseCommand.startVoting(sessionId, startedAt);
        if (endsAt == null || !endsAt.isAfter(startedAt)) {
            throw new IllegalStateException("Invalid voting deadline");
        }
        for (var participant : context.participants()) {
            events.publishEvent(new UserMessageEvent(participant.memberId(), VOTING_STARTED_DESTINATION,
                    new LiveVotingStartedMessage(sessionId, endsAt)));
        }
    }
}
