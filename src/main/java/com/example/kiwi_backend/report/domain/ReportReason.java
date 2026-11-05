package com.example.kiwi_backend.report.domain;

/** 신고 사유. 프론트 신고 팝업의 선택지와 같다 */
public enum ReportReason {
    RULE_VIOLATION("규칙을 위반했어요."),
    LEFT_EARLY("대화 중간에 나갔어요."),
    SEXUAL_HARASSMENT("성희롱을 했어요."),
    GENDER_FRAUD("성별을 속였어요."),
    PERSISTENT_MEETING_REQUEST("계속 만나자고 말해요."),
    VERBAL_ABUSE("사이버 언어폭력을 해요.");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
