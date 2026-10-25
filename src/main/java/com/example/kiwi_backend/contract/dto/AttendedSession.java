package com.example.kiwi_backend.contract.dto;

import java.time.Instant;

/** 회원이 출석해 투표까지 끝난(COMPLETED) 세션 한 건. 매칭 기록 화면에서 쓴다 */
public record AttendedSession(
        Long sessionId,
        Instant startTime
) {
}
