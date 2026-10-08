package com.example.kiwi_backend.session.api.dto;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

/**
 * 운영자 세션 생성·수정 요청.
 * 시작 시각은 시간대를 포함해서 받는다. 예: "2026-10-24T21:00:00+09:00"
 */
public record SessionCreateRequest(
        @NotNull @Future OffsetDateTime startTime,
        @Min(3) @Max(6) int capacityPerGender,
        @Positive int roundDurationSeconds,
        @Positive int votingDurationSeconds,
        @Positive int resultAndShareDurationSeconds,
        @PositiveOrZero int entryFeeGem,
        @PositiveOrZero int completionRefundGem
) {
}
