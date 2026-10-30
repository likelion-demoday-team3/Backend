package com.example.kiwi_backend.common.notification;

/**
 * 알림함에 남기는 알림 종류. 앱을 닫고 있어도 나중에 확인해야 하는 것만 둔다
 * (하트·라운드 배정처럼 세션 화면 안에서만 의미 있는 건 실시간 메시지로 충분하다).
 */
public enum NotificationType {
    // ===== 세션 진행·취소 =====
    /** 시작 30분 전 진행 확정 */
    SESSION_CONFIRMED,
    /** 시작 30분 전 신청 인원 미달로 자동 취소 (전원 환불) */
    SESSION_CANCELLED_LOW_APPLICANTS,
    /** 운영자 취소, 또는 신청자가 있어 삭제 대신 취소 (전원 환불) */
    SESSION_CANCELLED_BY_ADMIN,
    /** 로비 마감 때 출석 인원 미달로 취소 (출석자만 환불) */
    SESSION_CANCELLED_LOW_ATTENDANCE,
    /** 세션은 진행됐는데 시작 시각까지 입장하지 않음 (환불 없음) */
    NO_SHOW,

    // ===== 결과·보상 =====
    /** 투표를 완료해 보증금(참가비 일부)을 돌려받음 */
    COMPLETION_REFUND,

    // ===== 연락처 교환 =====
    CONTACT_REQUESTED,
    CONTACT_REQUEST_ACCEPTED,
    CONTACT_REQUEST_REJECTED,
    /** 상대가 직접 연락처를 보냄 (요청 수락·자동 매칭으로 공유된 건 다른 알림이 대신한다) */
    CONTACT_RECEIVED
}
