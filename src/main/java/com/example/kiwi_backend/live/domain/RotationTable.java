package com.example.kiwi_backend.live.domain;

import java.util.List;

public record RotationTable(
//        세션 전체 배정표. 정보: sessionId(단일), rounds(round1, round2, round3, ...)
        Long sessionId,
        List<RoundAssignment> rounds
) {
    public RotationTable {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        rounds = List.copyOf(rounds);
    }
}
