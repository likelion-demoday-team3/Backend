package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLivePhaseCommand;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveVotingStartService {

    public static final String VOTING_STARTED_DESTINATION = "/queue/live/voting-started";

    private final ObjectProvider<SessionLivePhaseCommand> phaseCommandProvider;
    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider;
    private final SimpMessagingTemplate messagingTemplate;

    public void startVoting(Long sessionId, Instant startedAt) {
        if (sessionId == null || startedAt == null) {
            throw new IllegalArgumentException("sessionId and startedAt are required");
        }
        SessionLivePhaseCommand phaseCommand = phaseCommandProvider.getIfAvailable();
        SessionQueryService sessionQueryService = sessionQueryServiceProvider.getIfAvailable();
        if (phaseCommand == null || sessionQueryService == null) {
            throw new IllegalStateException("세션 투표 전환 구현체가 필요합니다");
        }

        List<LiveParticipant> participants = sessionQueryService.getLiveParticipants(sessionId);
        if (participants == null || participants.isEmpty()) {
            throw new IllegalStateException("출석한 참가자가 없습니다");
        }
        Instant votingEndsAt = phaseCommand.startVoting(sessionId, startedAt);
        if (votingEndsAt == null || !votingEndsAt.isAfter(startedAt)) {
            throw new IllegalStateException("투표 마감 시각이 올바르지 않습니다");
        }

        LiveVotingStartedMessage message = new LiveVotingStartedMessage(sessionId, votingEndsAt);
        for (LiveParticipant participant : participants) {
            messagingTemplate.convertAndSendToUser(
                    participant.memberId().toString(), VOTING_STARTED_DESTINATION, message);
        }
    }
}
