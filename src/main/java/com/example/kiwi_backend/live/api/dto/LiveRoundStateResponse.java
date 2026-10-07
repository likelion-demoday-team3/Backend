package com.example.kiwi_backend.live.api.dto;

import com.example.kiwi_backend.live.application.LiveRoundStateResult;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;

public record LiveRoundStateResponse(
        Long sessionId,
        int roundNumber,
        LiveRoundStatus status,
        boolean assignmentBroadcasted
) {
    public static LiveRoundStateResponse from(LiveRoundStateResult result) {
        return new LiveRoundStateResponse(
                result.sessionId(),
                result.roundNumber(),
                result.status(),
                result.assignmentBroadcasted()
        );
    }
}
