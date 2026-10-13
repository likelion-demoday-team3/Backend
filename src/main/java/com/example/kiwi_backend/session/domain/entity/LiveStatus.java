package com.example.kiwi_backend.session.domain.entity;

/**
 * 라이브 진행 단계.
 * 누가 언제 바꿀지는 A와 정하는 중이라, 지금은 생성 시 WAITING으로만 둔다.
 * (A의 live 모듈은 라운드 상태를 live_round 테이블로 관리하고 있음)
 */
public enum LiveStatus {
    WAITING,
    LOBBY,
    IN_ROUND,
    HEART,
    VOTING,
    RESULT_AND_SHARE,
    CLOSED
}
