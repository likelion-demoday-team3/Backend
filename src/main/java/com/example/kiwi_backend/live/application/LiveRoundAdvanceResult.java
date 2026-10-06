package com.example.kiwi_backend.live.application;

public record LiveRoundAdvanceResult(
//        “현재 라운드 끝내고 다음 라운드 시작” 결과
        Long sessionId,
        int completedRoundNumber,
        boolean nextRoundStarted,
        Integer nextRoundNumber
) {
}
