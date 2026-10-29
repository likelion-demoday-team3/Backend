package com.example.kiwi_backend.session.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CallTestSessionRequest(
        @NotBlank @Email String hostEmail,
        @NotBlank @Email String firstPartnerEmail,
        @NotBlank @Email String secondPartnerEmail
) {
}
