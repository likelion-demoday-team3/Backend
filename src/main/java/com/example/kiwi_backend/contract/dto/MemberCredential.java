package com.example.kiwi_backend.contract.dto;

public record MemberCredential(
        Long memberId,
        String passwordHash,
        String role,
        /** 로그인할 수 있는가. 탈퇴 회원만 막는다 (정지 회원은 로그인은 되고 세션 신청만 막힌다) */
        boolean loginAllowed
) {
}
