package com.example.kiwi_backend.session.application.scheduler;

import com.example.kiwi_backend.session.application.service.SessionDecisionService;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 1분마다 "시작 30분 전이 지났는데 아직 모집 중인 세션"을 찾아 진행 여부를 판단한다.
 *
 * 정확한 시각에 예약하지 않고 주기적으로 조회하는 이유:
 * 서버가 꺼져 있다가 켜져도 다음 조회 때 밀린 세션을 자동으로 처리한다 (재시작 복구가 따로 필요 없음).
 *
 * 세션마다 별도 트랜잭션으로 처리해서, 한 세션이 실패해도 나머지는 계속 진행된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionDecisionScheduler {

    private final DatingSessionRepository sessionRepository;
    private final SessionDecisionService decisionService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void decideDueSessions() {
        Instant now = Instant.now();
        List<Long> dueSessionIds = sessionRepository.findIdsByStatusAndStartTimeAtOrBefore(
                SessionStatus.RECRUITING, now.plus(DatingSession.APPLY_CLOSE_BEFORE));

        for (Long sessionId : dueSessionIds) {
            try {
                decisionService.decide(sessionId, now);
            } catch (RuntimeException e) {
                log.error("세션 진행 판단 실패 sessionId={}", sessionId, e);
            }
        }
    }
}
