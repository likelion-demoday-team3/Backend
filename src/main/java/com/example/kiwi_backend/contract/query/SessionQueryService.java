package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.LiveParticipant;

import java.time.Instant;
import java.util.List;

/**
 * session 모듈이 구현하고 live/realtime이 사용하는 세션 조회 계약.
 *
 * [제안 4] A가 만든 버전(isParticipant, getStartTime, getLiveParticipants)에
 *   로비 마감·1라운드 시작을 주기 조회하기 위한 메서드 두 개를 추가하고,
 *   LiveParticipant import를 contract.dto로 바꾼 버전이다.
 */
public interface SessionQueryService {

    /**
     * 이 회원이 "지금" 라이브에 들어올 수 있는지.
     *   - 세션 READY: 신청 상태(APPLIED)이고 로비 시간(시작 5분 전 ~ 시작 1분 후) 안
     *   - 세션 IN_PROGRESS: 출석(ATTENDED) (라운드 중 재접속)
     *   - 그 외(취소된 세션, 본인 취소, 노쇼 등): false
     * 메서드 이름은 A의 코드가 이미 쓰고 있어서 그대로 두었다. 의미는 "라이브 입장 가능 여부"로 넓어졌다.
     */
    boolean isParticipant(Long sessionId, Long memberId);

    /** 세션 시작 시각. 없는 세션이면 NotFoundException */
    Instant getStartTime(Long sessionId);

    /** 로비를 마감할 차례인 세션: READY이고 시작 + 1분이 지남. A의 5초 주기 조회 ①에서 사용 */
    List<Long> findSessionIdsDueForLobbyClose(Instant now);

    /** 1라운드를 시작할 차례인 세션: IN_PROGRESS이고 아직 라운드 시작 전이며 1라운드 시각이 지남. 조회 ②에서 사용 */
    List<Long> findSessionIdsDueForFirstRound(Instant now);

    /**
     * 출석자(ATTENDED) 목록.
     * finalizeAttendance 결과에 같은 목록이 들어 있으므로, 로비 마감 직후에는 이걸 따로 부르지 않아도 된다.
     */
    List<LiveParticipant> getLiveParticipants(Long sessionId);
}
