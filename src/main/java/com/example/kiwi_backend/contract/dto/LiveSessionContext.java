package com.example.kiwi_backend.contract.dto;

import java.time.Instant;
import java.util.List;

public record LiveSessionContext(Long sessionId, Phase phase, Instant deadline,
                                 int completionRewardGem, List<LiveParticipant> participants) {
    public enum Phase { WAITING, LOBBY, IN_ROUND, HEART, VOTING, RESULT_AND_SHARE, CLOSED }

    public LiveParticipant requireParticipant(Long memberId) {
        return participants.stream().filter(p -> p.memberId().equals(memberId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Attended participant required"));
    }

    public void requireOppositePair(Long senderId, Long receiverId) {
        LiveParticipant sender = requireParticipant(senderId);
        LiveParticipant receiver = requireParticipant(receiverId);
        if (senderId.equals(receiverId) || sender.gender() == receiver.gender()) {
            throw new IllegalArgumentException("An opposite-gender participant is required");
        }
    }
}
