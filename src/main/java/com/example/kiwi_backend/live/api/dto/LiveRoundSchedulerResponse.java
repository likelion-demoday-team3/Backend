package com.example.kiwi_backend.live.api.dto;

import com.example.kiwi_backend.live.application.LiveRoundSchedulerResult;

public record LiveRoundSchedulerResponse(
        Long sessionId,
        int firstRoundNumber,
        int roundDurationSeconds
) {

    public static LiveRoundSchedulerResponse from(LiveRoundSchedulerResult result) {
        return new LiveRoundSchedulerResponse(
                result.sessionId(),
                result.firstRoundNumber(),
                result.roundDurationSeconds()
        );
    }
}
