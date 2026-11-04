package com.example.kiwi_backend.contract.command;

/**
 * 라이브 진행 단계 기록. session이 구현하고 live(A)가 호출한다.
 */
public interface SessionLiveStatusCommand {

    /**
     * 첫 라운드 변경과 같은 트랜잭션에서 호출하며 세션 행을 잠근다.
     * 예정 시각 전이나 이미 시작된 세션은 거절한다.
     */
    void markRoundsStarted(Long sessionId);

    /**
     * 라운드가 시작될 때 호출해 세션의 현재 라운드 번호를 기록한다 (세션 상세 currentRoundNumber).
     * 새로고침·재입장 때 화면이 몇 번째 라운드인지 되살리는 데 쓴다. 더 작은 번호로는 되돌리지 않는다.
     */
    void markRoundInProgress(Long sessionId, int roundNumber);
}
