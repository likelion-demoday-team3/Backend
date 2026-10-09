package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.live.domain.LiveParticipant;

import java.time.Instant;
import java.util.List;

/**
 * session 모듈이 구현하고 live/realtime이 사용하는 세션 조회 계약.
 */
public interface SessionQueryService {

    boolean isParticipant(Long sessionId, Long memberId);

    Instant getStartTime(Long sessionId);

    List<LiveParticipant> getLiveParticipants(Long sessionId);
}
