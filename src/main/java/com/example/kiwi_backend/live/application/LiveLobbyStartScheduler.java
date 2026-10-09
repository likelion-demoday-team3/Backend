package com.example.kiwi_backend.live.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveLobbyStartScheduler {

    private final TaskScheduler taskScheduler;
    private final LiveSessionStartService liveSessionStartService;
    private final Set<Long> scheduledOrStarted = ConcurrentHashMap.newKeySet();

    public void scheduleIfNeeded(Long sessionId, Instant closeAt) {
        if (!scheduledOrStarted.add(sessionId)) {
            return;
        }
        try {
            if (taskScheduler.schedule(() -> start(sessionId), closeAt) == null) {
                throw new IllegalStateException("로비 마감 작업을 예약할 수 없습니다");
            }
        } catch (RuntimeException e) {
            scheduledOrStarted.remove(sessionId);
            throw e;
        }
    }

    private void start(Long sessionId) {
        try {
            liveSessionStartService.startLiveSession(sessionId);
        } catch (RuntimeException e) {
            scheduledOrStarted.remove(sessionId);
            log.error("세션 {} 로비 마감 및 라이브 시작 실패", sessionId, e);
        }
    }
}
