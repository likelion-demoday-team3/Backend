package com.example.kiwi_backend.contract.dto;

/** 운영자 화면(신고 처리)에서 보여줄 회원 정보. status는 ACTIVE·SUSPENDED·WITHDRAWN */
public record MemberAdminSummary(
        Long memberId,
        String name,
        String email,
        String status
) {
}
