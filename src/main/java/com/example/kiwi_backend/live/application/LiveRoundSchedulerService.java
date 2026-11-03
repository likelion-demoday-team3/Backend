package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionLiveStatusCommand;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
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
    private final LiveHeartPhaseService liveHeartPhaseService;
    private final TaskScheduler taskScheduler;
    private final SessionLiveStatusCommand sessionLiveStatusCommand;
    private final SessionQueryService sessionQueryService;
    private final Set<Long> runningSessionIds = ConcurrentHashMap.newKeySet();

    @Transactional
    public LiveRoundSchedulerResult startSessionRounds(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        int roundDurationSeconds = sessionQueryService.getRoundDurationSeconds(sessionId);
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
            Instant startedAt = Instant.now();
            liveRoundStateService.startRound(sessionId, firstRoundNumber, startedAt);
            LiveAfterCommit.run(() -> scheduleRoundEnd(sessionId, firstRoundNumber, startedAt, roundDurationSeconds));
        } catch (RuntimeException e) {
            runningSessionIds.remove(sessionId);
            throw e;
        }

        return new LiveRoundSchedulerResult(sessionId, firstRoundNumber, roundDurationSeconds);
    }

    private void startRoundAndScheduleEnd(Long sessionId, int roundNumber, int roundDurationSeconds) {
        Instant startedAt = Instant.now();
        liveRoundStateService.startRound(sessionId, roundNumber, startedAt);
        scheduleRoundEnd(sessionId, roundNumber, startedAt, roundDurationSeconds);
    }

    private void scheduleRoundEnd(Long sessionId, int roundNumber, Instant startedAt, int roundDurationSeconds) {
        taskScheduler.schedule(
                () -> completeRoundAndScheduleNext(sessionId, roundNumber, roundDurationSeconds),
                startedAt.plusSeconds(roundDurationSeconds)
        );
    }

    private void completeRoundAndScheduleNext(Long sessionId, int roundNumber, int roundDurationSeconds) {
        LiveRoundAdvanceResult result = liveRoundStateService.completeAndPrepareNextRound(
                sessionId,
                roundNumber,
                Instant.now()
        );

        if (result.nextRoundExists()) {
            scheduleNextRoundStart(sessionId, result.nextRoundNumber(), result.breakSeconds(), roundDurationSeconds);
            return;
        }
        try {
            liveHeartPhaseService.startHearts(sessionId, Instant.now());
        } finally {
            runningSessionIds.remove(sessionId);
        }
    }

    // [휴식 제거] breakSeconds가 0이라 다음 라운드는 이전 라운드가 끝난 즉시 시작된다.
    private void scheduleNextRoundStart(Long sessionId, int nextRoundNumber, int breakSeconds, int roundDurationSeconds) {
        taskScheduler.schedule(
                () -> startRoundAndScheduleEnd(sessionId, nextRoundNumber, roundDurationSeconds),
                Instant.now().plusSeconds(breakSeconds)
        );
    }
}
