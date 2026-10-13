package com.example.kiwi_backend.contract.dto;

/** 세션 신청 등에서 필요한 회원 요약 정보. 연락처 같은 민감 정보는 넣지 않는다 */
public record MemberSummary(
        Long memberId,
        Gender gender,
        boolean active,
        boolean identityVerified
) {
}
