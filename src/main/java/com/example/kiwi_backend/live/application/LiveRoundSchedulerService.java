package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLiveStatusCommand;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LiveRoundSchedulerService {

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoundStateService liveRoundStateService;
    private final LiveVotingStartService liveVotingStartService;
    private final TaskScheduler taskScheduler;
    private final SessionLiveStatusCommand sessionLiveStatusCommand;
    private final Set<Long> runningSessionIds = ConcurrentHashMap.newKeySet();

    @Value("${app.live.round-duration-seconds:180}")
    private int roundDurationSeconds;

    @Transactional
    public LiveRoundSchedulerResult startSessionRounds(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (roundDurationSeconds < 1) {
            throw new IllegalStateException("roundDurationSeconds must be positive");
        }

        // The session lock and the first round update share this transaction.
        sessionLiveStatusCommand.markRoundsStarted(sessionId);
        List<LiveRound> rounds = liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId);
        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("round not found");
        }
        if (!runningSessionIds.add(sessionId)) {
            throw new IllegalStateException("이미 자동 진행 중인 라이브 세션입니다");
        }
//        같은 세션에서 자동 진행 시작 API를 두 번 누르면 스케줄이 두 겹으로 잡힐 수 있어서, 이미 진행 중이면 막는다.

        int firstRoundNumber = rounds.get(0).getRoundNumber();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    runningSessionIds.remove(sessionId);
                }
            }
        });
        try {
            liveRoundStateService.startRound(sessionId, firstRoundNumber, Instant.now());
            LiveAfterCommit.run(() -> scheduleRoundEnd(sessionId, firstRoundNumber));
        } catch (RuntimeException e) {
            runningSessionIds.remove(sessionId);
            throw e;
        }

        return new LiveRoundSchedulerResult(sessionId, firstRoundNumber, roundDurationSeconds);
    }

    private void startRoundAndScheduleEnd(Long sessionId, int roundNumber) {
        liveRoundStateService.startRound(sessionId, roundNumber, Instant.now());
        scheduleRoundEnd(sessionId, roundNumber);
    }

    private void scheduleRoundEnd(Long sessionId, int roundNumber) {
        taskScheduler.schedule(
                () -> completeRoundAndScheduleNext(sessionId, roundNumber),
                Instant.now().plusSeconds(roundDurationSeconds)
        );
    }

    private void completeRoundAndScheduleNext(Long sessionId, int roundNumber) {
        LiveRoundAdvanceResult result = liveRoundStateService.completeAndPrepareNextRound(
                sessionId,
                roundNumber,
                Instant.now()
        );

        if (result.nextRoundExists()) {
            scheduleNextRoundStart(sessionId, result.nextRoundNumber(), result.breakSeconds());
            return;
        }
        try {
            liveVotingStartService.startVoting(sessionId, Instant.now());
        } finally {
            runningSessionIds.remove(sessionId);
        }
    }

    private void scheduleNextRoundStart(Long sessionId, int nextRoundNumber, int breakSeconds) {
        taskScheduler.schedule(
                () -> startRoundAndScheduleEnd(sessionId, nextRoundNumber),
                Instant.now().plusSeconds(breakSeconds)
        );
    }
}
