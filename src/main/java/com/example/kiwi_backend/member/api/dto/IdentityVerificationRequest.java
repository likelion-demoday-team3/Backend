package com.example.kiwi_backend.member.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record IdentityVerificationRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{1,40}$")
        String identityVerificationId
) {
}
