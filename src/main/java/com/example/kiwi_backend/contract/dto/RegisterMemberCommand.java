package com.example.kiwi_backend.contract.dto;

import java.time.Instant;
import java.time.LocalDate;

/* 회원을 만들려면 필요한 정보
** auth는 이 양식을 채워서 넘기기만 하고
** member는 이 양식을 받아서 회원을 어떻게 만들지만 결정
 */
public record RegisterMemberCommand(
        String email,
        String name,
        String passwordHash,
        String university,
        Gender gender,
        LocalDate birthDate,
        String phoneNumber,
        String instagramId,
        Instant emailVerifiedAt
) {
}
