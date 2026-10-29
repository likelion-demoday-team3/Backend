package com.example.kiwi_backend.session.api.dto;

import java.time.Instant;

public record CallTestSessionResponse(
        Long sessionId,
        Instant startTime,
        Instant lobbyClosesAt,
        Instant firstRoundAt
) {
}
