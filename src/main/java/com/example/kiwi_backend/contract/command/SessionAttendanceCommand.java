package com.example.kiwi_backend.contract.command;

import com.example.kiwi_backend.contract.dto.AttendanceResult;

import java.util.Set;

/**
 * 로비 마감 시 출석 확정. session이 구현하고 live(A)가 20:00(시작 시각)에 호출한다.
 *
 * [제안 4] A가 만든 원래 버전은 반환값이 void였다. 진행 판단 결과를 A가 받을 수 없어서
 *   출석 미달이어도 배정표를 만들고 라운드를 시작하는 문제가 있어 AttendanceResult를 반환하도록 바꿨다.
 *   이 파일은 A와 같이 쓰는 계약이라, 이 변경만 담은 PR을 먼저 올려서 둘 다 받은 뒤 각자 구현할 것.
 */
public interface SessionAttendanceCommand {

    /**
     * connectedMemberIds: 로비 마감 순간 접속해 있던 회원 (A의 LiveLobbyPresenceService.closeLobby 결과)
     *
     * 하는 일
     *   - 신청자 중 접속자는 ATTENDED, 나머지는 NO_SHOW (노쇼는 환불 없음)
     *   - 출석자로 진행 조건(총 5명 이상, 성비 차이 1명 이하) 판단
     *   - 통과 → 세션 IN_PROGRESS, 1라운드 시각(시작 + 1분 30초) 저장
     *   - 미달 → 세션 CANCELLED, 출석자만 100% 환불 (접속자 0명도 같은 규칙: 전원 노쇼, 환불 없음)
     *
     * A의 트랜잭션 안에서 호출된다. 이후 배정표 저장이 실패하면 출석 처리도 함께 되돌아가서
     * 세션이 READY로 남고, 다음 조회 때 다시 시도된다.
     */
    AttendanceResult finalizeAttendance(Long sessionId, Set<Long> connectedMemberIds);
}
