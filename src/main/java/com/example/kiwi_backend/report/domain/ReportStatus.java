package com.example.kiwi_backend.report.domain;

public enum ReportStatus {
    /** 운영자가 아직 확인하지 않음 */
    PENDING,
    /** 신고된 회원을 이용 정지함 */
    ACTIONED,
    /** 확인했지만 조치하지 않음 */
    DISMISSED
}
