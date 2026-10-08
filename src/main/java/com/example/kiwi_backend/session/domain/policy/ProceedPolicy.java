package com.example.kiwi_backend.session.domain.policy;

/**
 * 세션 진행 조건: 총 5명 이상이고 성비 차이가 1명 이하.
 * 30분 전 판단과 로비 마감 판단이 같은 공식을 쓰므로 한곳에 둔다.
 */
public final class ProceedPolicy {

    static final int MIN_TOTAL = 5;
    static final int MAX_GENDER_GAP = 1;

    private ProceedPolicy() {
    }

    public static boolean canProceed(int male, int female) {
        return male + female >= MIN_TOTAL && Math.abs(male - female) <= MAX_GENDER_GAP;
    }
}
