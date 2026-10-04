package com.example.kiwi_backend.member.api.dto;

import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.enumerated.Gender;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 본인 정보. 연락처가 포함되므로 "내 정보 조회"에서만 쓴다.
 * 다른 사람에게 보여주는 응답에는 절대 재사용하지 않는다.
 */
public record MyInfoResponse(
        UUID publicId,
        String email,
        String university,
        Gender gender,
        LocalDate birthDate,
        String phoneNumber,
        String instagramId
) {
    public static MyInfoResponse from(Member member) {
        return new MyInfoResponse(
                member.getPublicId(),
                member.getEmail(),
                member.getUniversity(),
                member.getGender(),
                member.getBirthDate(),
                member.getPhoneNumber(),
                member.getInstagramId()
        );
    }
}
