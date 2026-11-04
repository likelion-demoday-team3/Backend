package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLiveStatusCommand;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LiveRoundStateService {

    // [휴식 제거] 라운드 사이 휴식(60초)을 없애고 이전 라운드가 끝나면 바로 다음 라운드를 시작한다.
    // 되살리려면 아래 주석을 풀고 BREAK_SECONDS 대신 DEFAULT_BREAK_SECONDS를 쓴다.
    // private static final int DEFAULT_BREAK_SECONDS = 60;
    private static final int BREAK_SECONDS = 0;

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoundBroadcastService liveRoundBroadcastService;
    private final SessionLiveStatusCommand sessionLiveStatusCommand;

    @Transactional
    public LiveRoundStateResult startRound(Long sessionId, int roundNumber, Instant startedAt) {
        LiveRound round = findRound(sessionId, roundNumber);
        round.start(startedAt);
        // 세션 상세의 currentRoundNumber를 함께 갱신해 새로고침·재입장 때 라운드 번호를 되살린다
        sessionLiveStatusCommand.markRoundInProgress(sessionId, roundNumber);
        LiveAfterCommit.run(() -> liveRoundBroadcastService.broadcastRoundAssignments(sessionId, roundNumber));
        LiveAfterCommit.run(() -> liveRoundBroadcastService.broadcastIdle(sessionId, roundNumber, BREAK_SECONDS));

        return new LiveRoundStateResult(sessionId, roundNumber, round.getStatus(), true);
    }

    @Transactional
    public LiveRoundStateResult completeRound(Long sessionId, int roundNumber, Instant endedAt) {
        LiveRound round = findRound(sessionId, roundNumber);
        round.complete(endedAt);

        return new LiveRoundStateResult(sessionId, roundNumber, round.getStatus(), false);
    }

    @Transactional
    public LiveRoundAdvanceResult completeAndPrepareNextRound(Long sessionId,
                                                              int currentRoundNumber,
                                                              Instant endedAt) {
        LiveRound currentRound = findRound(sessionId, currentRoundNumber);
        currentRound.complete(endedAt);

        int nextRoundNumber = currentRoundNumber + 1;
        boolean nextRoundExists = liveRoundRepository.findBySessionIdAndRoundNumber(sessionId, nextRoundNumber)
                .isPresent();
        if (nextRoundExists) {
            return new LiveRoundAdvanceResult(
                    sessionId,
                    currentRoundNumber,
                    true,
                    nextRoundNumber,
                    // [휴식 제거] LiveRoundPhase.BREAK,
                    // [휴식 제거] DEFAULT_BREAK_SECONDS
                    LiveRoundPhase.ROUND,
                    BREAK_SECONDS
            );
        }
        return new LiveRoundAdvanceResult(
                sessionId,
                currentRoundNumber,
                false,
                null,
                LiveRoundPhase.FINISHED,
                0
        );
    }

    private LiveRound findRound(Long sessionId, int roundNumber) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        return liveRoundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("round not found"));
    }
}
