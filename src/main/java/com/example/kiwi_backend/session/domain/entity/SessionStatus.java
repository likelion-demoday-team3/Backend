package com.example.kiwi_backend.session.domain.entity;

/**
 * 세션 생명주기. session 모듈만 바꾼다.
 *
 * SCHEDULED   : 생성됨, 아직 모집 전 (설정 수정 가능)
 * RECRUITING  : 모집 중, 시작 30분 전까지 신청·취소 가능
 * READY       : 시작 30분 전 신청 마감, 진행 조건 통과. 로비 대기 (시작 5분 전 오픈 ~ 시작 시각 마감)
 * IN_PROGRESS : 로비 마감 후 출석자로 진행 조건 통과. 라운드 진행 중
 * CANCELLED   : 진행 조건 미달 또는 운영자 취소
 * COMPLETED   : 세션 종료 (언제 바꿀지는 보류)
 */
public enum SessionStatus {
    SCHEDULED,
    RECRUITING,
    READY,
    IN_PROGRESS,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(SessionStatus next) {
        return switch (this) {
            case SCHEDULED -> next == RECRUITING || next == CANCELLED;
            case RECRUITING -> next == READY || next == CANCELLED;
            case READY -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED; // 진행 중 취소는 A와 연동 전까지 막아둠
            case CANCELLED, COMPLETED -> false;
        };
    }
}
