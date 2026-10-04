package com.example.kiwi_backend.member.application;

public record MemberCredential(
        Long memberId,
        String passwordHash,
        String role,
        boolean active
) {
}
