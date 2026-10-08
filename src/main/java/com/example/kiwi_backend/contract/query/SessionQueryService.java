package com.example.kiwi_backend.contract.query;

/**
 * 세션 정보 조회. session이 구현하고 live, realtime, vote 등이 사용한다.
 * 로비·배정표 관련 메서드는 A와 정한 뒤 추가한다.
 */
public interface SessionQueryService {

    /**
     * 이 회원이 이 세션의 유효한 참가자인지 (신청 또는 출석 상태).
     * 취소했거나 노쇼면 false. WebSocket join 때 사용한다.
     */
    boolean isParticipant(Long sessionId, Long memberId);
}
