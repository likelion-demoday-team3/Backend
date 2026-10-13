package com.example.kiwi_backend.member.api.dto;

import java.time.Instant;

public record IdentityVerificationResponse(boolean verified, Instant verifiedAt) {
}
