package com.example.kiwi_backend.contract.command;

import java.time.Instant;

/** 라이브 진행 단계 전환. session이 구현하고 live(A)가 호출한다 */
public interface SessionLivePhaseCommand {

    /**
     * 마지막 라운드가 끝나서 투표를 시작한다.
     * liveStatus를 VOTING으로 바꾸고, 투표 마감 시각(startedAt + votingDurationSeconds)을 돌려준다.
     * 이미 투표 중이면 처음 정한 마감 시각을 그대로 돌려준다 (두 번 불려도 안전).
     */
    Instant startVoting(Long sessionId, Instant startedAt);
}
