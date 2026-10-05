package com.example.kiwi_backend.realtime.dto;

import jakarta.validation.constraints.NotNull;

public record LiveJoinRequest(
        @NotNull Long sessionId
//        memberId는 클라이언트가 보내지 않는다.
) {
}
