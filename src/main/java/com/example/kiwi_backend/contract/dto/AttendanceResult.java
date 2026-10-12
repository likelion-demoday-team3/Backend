package com.example.kiwi_backend.contract.dto;

import java.time.Instant;
import java.util.List;

/**
 * 로비 마감(finalizeAttendance) 결과.
 *
 * A가 이 결과로 분기하는 방법
 *   alreadyProcessed = true → 다른 호출이 이미 처리함. 아무것도 하지 말고 넘어갈 것 (취소 안내도 보내지 않음)
 *   proceed = true          → attendees로 배정표를 만들고 저장. 1라운드는 firstRoundAt에 시작
 *   proceed = false         → session이 이미 세션 취소와 출석자 환불을 마쳤음. 취소 안내만 보낼 것
 *
 * [제안 1] alreadyProcessed를 둔 이유:
 *   같은 세션으로 두 번 불리면(조회 겹침, 서버 여러 대) 두 번째 호출은 이미 READY가 아닌 세션을 보게 된다.
 *   이때 proceed=false만 주면 A가 "취소됐다"로 착각해서 취소 안내를 보낼 수 있어서, 별도 표시로 구분한다.
 *
 * [제안 2] 안내 메시지는 A 쪽에서 트랜잭션 커밋 "이후"에 보낼 것.
 *   startLiveSession 안에서 바로 보내면, 그 뒤 배정표 저장이 실패해 전부 되돌아가도
 *   참가자는 이미 "30초 후 시작" 같은 메시지를 받은 상태가 된다.
 *   (TransactionSynchronization.afterCommit 또는 @TransactionalEventListener(AFTER_COMMIT) 사용)
 */
public record AttendanceResult(
        Long sessionId,
        boolean alreadyProcessed,
        boolean proceed,
        List<LiveParticipant> attendees,
        Instant firstRoundAt
) {
    public static AttendanceResult proceeded(Long sessionId, List<LiveParticipant> attendees, Instant firstRoundAt) {
        return new AttendanceResult(sessionId, false, true, List.copyOf(attendees), firstRoundAt);
    }

    public static AttendanceResult cancelled(Long sessionId) {
        return new AttendanceResult(sessionId, false, false, List.of(), null);
    }

    public static AttendanceResult alreadyProcessed(Long sessionId) {
        return new AttendanceResult(sessionId, true, false, List.of(), null);
    }
}
