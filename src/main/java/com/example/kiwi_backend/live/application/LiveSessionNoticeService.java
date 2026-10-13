package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.LiveParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSessionNoticeService {
    private final SimpMessagingTemplate messagingTemplate;

    public void cancelledAfterCommit(Long sessionId, Set<Long> recipients) {
        Set<Long> snapshot = Set.copyOf(recipients);
        LiveAfterCommit.run(() -> send(snapshot, "/queue/live/cancelled",
                new CancelledMessage(sessionId, "INSUFFICIENT_ATTENDANCE")));
    }

    public void startingSoonAfterCommit(Long sessionId, List<LiveParticipant> participants, Instant firstRoundAt) {
        List<Long> recipients = participants.stream().map(LiveParticipant::memberId).toList();
        LiveAfterCommit.run(() -> send(recipients, "/queue/live/starting-soon",
                new StartingSoonMessage(sessionId, firstRoundAt)));
    }

    private void send(Collection<Long> recipients, String destination, Object message) {
        for (Long memberId : recipients) {
            try {
                messagingTemplate.convertAndSendToUser(memberId.toString(), destination, message);
            } catch (RuntimeException e) {
                log.error("Live notice failed: memberId={}, destination={}", memberId, destination, e);
            }
        }
    }

    public record CancelledMessage(Long sessionId, String reason) { }
    public record StartingSoonMessage(Long sessionId, Instant firstRoundAt) { }
}
