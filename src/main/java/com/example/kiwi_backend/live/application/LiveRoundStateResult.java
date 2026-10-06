package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;

public record LiveRoundStateResult(
//        단일 라운드 시작/종료 결괴
        Long sessionId,
        int roundNumber,
        LiveRoundStatus status,
        boolean assignmentBroadcasted
) {
}
