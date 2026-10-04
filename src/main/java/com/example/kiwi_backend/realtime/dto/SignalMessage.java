package com.example.kiwi_backend.realtime.dto;

public record SignalMessage(
//        server->clientB
        Long sessionId,
        Long roomId,
        Long fromMemberId,
        Long toMemberId,
        SignalType type,
        String payload
) {
}
