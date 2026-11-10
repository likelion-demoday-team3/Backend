package com.example.kiwi_backend.common.terms;

/**
 * 지금 시행 중인 약관 버전 (약관 시행일).
 * 약관(프론트 src/features/terms/content.ts)을 바꾸면 이 값도 올린다.
 * 회원이 동의한 버전과 다르면 로그인할 때 다시 동의를 받는다.
 */
public final class TermsVersion {

    public static final String CURRENT = "2026-10-31";

    private TermsVersion() {
    }

    /** 지금 약관에 다시 동의해야 하면 true (동의 기록이 없거나 예전 버전에 동의함) */
    public static boolean agreementRequired(String agreedVersion) {
        return !CURRENT.equals(agreedVersion);
    }
}
