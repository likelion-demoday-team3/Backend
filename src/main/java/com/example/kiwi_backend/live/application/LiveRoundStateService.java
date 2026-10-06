package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LiveRoundStateService {

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoundBroadcastService liveRoundBroadcastService;

    @Transactional
    public LiveRoundStateResult startRound(Long sessionId, int roundNumber, Instant startedAt) {
        LiveRound round = findRound(sessionId, roundNumber);
        round.start(startedAt);
        liveRoundBroadcastService.broadcastRoundAssignments(sessionId, roundNumber);

        return new LiveRoundStateResult(sessionId, roundNumber, round.getStatus(), true);
    }

    @Transactional
    public LiveRoundStateResult completeRound(Long sessionId, int roundNumber, Instant endedAt) {
        LiveRound round = findRound(sessionId, roundNumber);
        round.complete(endedAt);

        return new LiveRoundStateResult(sessionId, roundNumber, round.getStatus(), false);
    }

    @Transactional
    public LiveRoundAdvanceResult completeAndStartNextRound(Long sessionId,
                                                            int currentRoundNumber,
                                                            Instant endedAt,
                                                            Instant nextStartedAt) {
        LiveRound currentRound = findRound(sessionId, currentRoundNumber);
        currentRound.complete(endedAt);

        int nextRoundNumber = currentRoundNumber + 1;
        return liveRoundRepository.findBySessionIdAndRoundNumber(sessionId, nextRoundNumber)
                .map(nextRound -> {
                    nextRound.start(nextStartedAt);
                    liveRoundBroadcastService.broadcastRoundAssignments(sessionId, nextRoundNumber);
                    return new LiveRoundAdvanceResult(sessionId, currentRoundNumber, true, nextRoundNumber);
                })
                .orElseGet(() -> new LiveRoundAdvanceResult(sessionId, currentRoundNumber, false, null));
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
