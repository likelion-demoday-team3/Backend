package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.live.domain.LiveParticipant;

import java.time.Instant;
import java.util.List;

/**
 * session 모듈이 구현하고 live/realtime이 사용하는 세션 조회 계약.
 */
public interface SessionQueryService {

    /**
     * 이 회원이 이 세션의 유효한 참가자인지 (신청 또는 출석 상태).
     * 취소했거나 노쇼면 false. WebSocket join 때 사용한다.
     */
    boolean isParticipant(Long sessionId, Long memberId);

    Instant getStartTime(Long sessionId);

    List<LiveParticipant> getLiveParticipants(Long sessionId);
}
