package com.example.kiwi_backend.contract.command;

/**
 * 라이브 진행 단계 기록. session이 구현하고 live(A)가 호출한다.
 */
public interface SessionLiveStatusCommand {

    /**
     * 1라운드를 시작한 직후 호출.
     * 이게 없으면 "1라운드 시작할 세션" 조회(findSessionIdsDueForFirstRound)에 같은 세션이 5초마다 계속 잡힌다.
     * 두 번 불려도 안전하다 (이미 기록돼 있으면 아무것도 안 함).
     */
    void markRoundsStarted(Long sessionId);
}
