package com.example.kiwi_backend.contract.dto;

public record CompatibilitySummary(int score, String reason) {

    public CompatibilitySummary {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("compatibility score must be between 0 and 100");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("compatibility reason is required");
        }
    }
}
