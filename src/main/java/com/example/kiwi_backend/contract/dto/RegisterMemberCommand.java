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
        Instant emailVerifiedAt,
        /** 학교 지역 (시·도). 프로필 카드에 보인다. 모르면 null */
        String region,
        /** 필수 약관(이용약관·개인정보 수집·이용·제3자 제공)에 동의한 시각. 모르면 null */
        Instant termsAgreedAt,
        /** 동의한 약관 버전 (약관 시행일, 예: 2026-10-31) */
        String termsVersion
) {
}
