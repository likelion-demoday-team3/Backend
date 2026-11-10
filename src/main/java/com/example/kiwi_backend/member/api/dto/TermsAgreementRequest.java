package com.example.kiwi_backend.member.api.dto;

import jakarta.validation.constraints.AssertTrue;

/** 지금 약관에 (다시) 동의. 가입 때와 같은 필수 동의 3개 */
public record TermsAgreementRequest(
        @AssertTrue(message = "이용약관에 동의해주세요") boolean agreeTerms,
        @AssertTrue(message = "개인정보 수집·이용에 동의해주세요") boolean agreePrivacy,
        @AssertTrue(message = "개인정보 제3자 제공에 동의해주세요") boolean agreeThirdParty
) {
}
