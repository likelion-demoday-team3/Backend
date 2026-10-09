package com.example.kiwi_backend.session.domain.entity;

/**
 * 세션 모집 생명주기. session 모듈만 바꾼다.
 *
 * SCHEDULED  : 생성됨, 아직 모집 전 (설정 수정 가능)
 * RECRUITING : 모집 중, 시작 30분 전까지 신청·취소 가능
 * READY      : 시작 30분 전 신청 마감, 진행 조건 통과
 * CANCELLED  : 진행 조건 미달 또는 운영자 취소
 * COMPLETED  : 세션 종료
 */
public enum SessionStatus {
    SCHEDULED,
    RECRUITING,
    READY,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(SessionStatus next) {
        return switch (this) {
            case SCHEDULED -> next == RECRUITING || next == CANCELLED;
            case RECRUITING -> next == READY || next == CANCELLED;
            case READY -> next == COMPLETED || next == CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}
