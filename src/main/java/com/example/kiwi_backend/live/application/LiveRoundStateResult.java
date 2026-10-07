package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;

public record LiveRoundStateResult(
        Long sessionId,
        int roundNumber,
        LiveRoundStatus status,
        boolean assignmentBroadcasted
) {
}
