package com.example.kiwi_backend.member.api.dto;

public record IdentityVerificationStartResponse(
        String storeId,
        String channelKey,
        String identityVerificationId
) {
}
