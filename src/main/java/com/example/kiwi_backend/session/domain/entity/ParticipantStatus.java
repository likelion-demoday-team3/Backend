package com.example.kiwi_backend.session.domain.entity;

/**
 * 참가자 개인 상태.
 *
 * APPLIED   : 신청함. 이 상태로 세션 시작까지 간다
 * CANCELLED : 본인 취소 (세션이 취소된 것과는 구분. 세션이 취소돼도 참가자 상태는 바꾸지 않는다)
 * ATTENDED  : 로비 마감 시 접속해 있음 (로비 처리는 A와 정한 뒤 구현)
 * NO_SHOW   : 로비 마감 시 미접속
 * LEFT      : 세션 중 이탈
 */
public enum ParticipantStatus {
    APPLIED,
    CANCELLED,
    ATTENDED,
    NO_SHOW,
    LEFT
}
