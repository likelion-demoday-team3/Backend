package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.member.domain.enumerated.Gender;

import java.time.Instant;
import java.time.LocalDate;

public record RegisterMemberCommand(
        String email,
        String passwordHash,
        String university,
        Gender gender,
        LocalDate birthDate,
        String phoneNumber,
        String instagramId,
        Instant emailVerifiedAt
) {
}
