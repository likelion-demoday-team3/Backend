package com.example.kiwi_backend.contract.dto;

public record MemberCredential(
        Long memberId,
        String passwordHash,
        String role,
        boolean active
) {
}
