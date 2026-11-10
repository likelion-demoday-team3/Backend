package com.example.kiwi_backend.member.api.dto;

import com.example.kiwi_backend.common.terms.TermsVersion;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.contract.dto.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 본인 정보. 연락처가 포함되므로 "내 정보 조회"에서만 쓴다.
 * 다른 사람에게 보여주는 응답에는 절대 재사용하지 않는다.
 */
public record MyInfoResponse(
        UUID publicId,
        String email,
        String name,
        String university,
        Gender gender,
        LocalDate birthDate,
        String phoneNumber,
        String instagramId,
        // 본인인증 시각. 인증하지 않았으면 null (마이페이지 "본인인증 완료/필요" 표시용)
        Instant identityVerifiedAt,
        // 동의한 약관 버전과 시각. 이 기능 이전 가입자는 null
        String termsVersion,
        Instant termsAgreedAt,
        // true면 지금 약관에 다시 동의해야 한다 (프론트가 로그인 후 동의 창을 띄운다)
        boolean termsAgreementRequired
) {
    public static MyInfoResponse from(Member member) {
        return new MyInfoResponse(
                member.getPublicId(),
                member.getEmail(),
                member.getName(),
                member.getUniversity(),
                member.getGender(),
                member.getBirthDate(),
                member.getPhoneNumber(),
                member.getInstagramId(),
                member.getIdentityVerifiedAt(),
                member.getTermsVersion(),
                member.getTermsAgreedAt(),
                TermsVersion.agreementRequired(member.getTermsVersion())
        );
    }
}
