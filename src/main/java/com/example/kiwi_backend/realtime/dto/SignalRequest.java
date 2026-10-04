package com.example.kiwi_backend.realtime.dto;

import jakarta.validation.constraints.NotNull;

public record SignalRequest(
//        client A->server
        @NotNull Long sessionId,
        @NotNull Long roomId,
        @NotNull Long toMemberId,
        @NotNull SignalType type,
        @NotNull String payload
) {
}
