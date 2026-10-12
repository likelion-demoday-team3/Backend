package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionAttendanceCommand;
import com.example.kiwi_backend.contract.dto.AttendanceResult;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.*;
import com.example.kiwi_backend.realtime.service.LiveLobbyPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LiveSessionStartService {

    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider;
    private final ObjectProvider<SessionAttendanceCommand> sessionAttendanceCommandProvider;
    private final LiveLobbyPresenceService lobbyPresenceService;
    private final Clock clock;
    private final RotationTableGenerator rotationTableGenerator;
    private final LiveScheduleService liveScheduleService;
    private final LiveRoundSchedulerService liveRoundSchedulerService;

    // 10/12 update:
    // 1. startLiveSession의 반환값은 void
    // 2. 시작 시각·마감 시각 확인은 지움 (finalizeAttendance가 확인함)
    @Transactional
    public void startLiveSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }

        SessionQueryService sessionQueryService = sessionQueryServiceProvider.getIfAvailable();
        if (sessionQueryService == null) {
            throw new IllegalStateException("SessionQueryService 구현체가 필요합니다");
        }

        SessionAttendanceCommand attendanceCommand = sessionAttendanceCommandProvider.getIfAvailable();
        if (attendanceCommand == null) {
            throw new IllegalStateException("SessionAttendanceCommand 구현체가 필요합니다");
        }

        // 로비 마감 순간 세션 로비에 연결되어 있는 회원
        Set<Long> connected = lobbyPresenceService.closeLobby(sessionId);

        // ① 결과 받기 : finalizeAttendance가 신청자(APPLIED) 목록을 기준으로 출석 확정
        AttendanceResult result = attendanceCommand.finalizeAttendance(sessionId, connected);

        if (result.alreadyProcessed()) {
            // 다른 호출이 이미 처리함
            return;
        }
        if (!result.proceed()) {
            // TODO for 10/12 update: 세션 취소와 출석자 환불은 session이 이미 끝냄. 안내만 (커밋 후에)
            afterCommit(() -> liveBroadcastService.broadcastCancelled(sessionId));
            return;
        }

        // ② getLiveParticipants 대신 : finalizeAttendance가 참가자(ATTENDED) 목록을 List<LiveParticipant> attendees에 담아줌
        List<LiveParticipant> participants = result.attendees();

        RotationTable rotationTable = rotationTableGenerator.generate(sessionId, participants);
        LiveScheduleResult scheduleResult = liveScheduleService.createSchedule(
                sessionId,
                toRoundPlans(rotationTable.rounds())
        );

        // TODO for 10/12 update: ③ startSessionRounds 호출 삭제 (주기 조회 ②에서 firstRoundAt에 시작)
        afterCommit(() -> liveBroadcastService.broadcastStartingSoon(sessionId, result.firstRoundAt()));
    }

    private List<LiveRoundPlan> toRoundPlans(List<RoundAssignment> assignments) {
        return assignments.stream()
                .map(this::toRoundPlan)
                .toList();
    }
//    RotationTableGenerator 결과 = 계산용 객체
//LiveScheduleService 입력 = 저장용 객체 이기에 변환이 필요합니다.

    private LiveRoundPlan toRoundPlan(RoundAssignment assignment) {
        return new LiveRoundPlan(
                assignment.roundNumber(),
                assignment.pairs().stream()
                        .map(this::toRoomPlan)
                        .toList(),
                assignment.idleMemberIds()
        );
    }

    private LiveRoomPlan toRoomPlan(MatchPair pair) {
        return new LiveRoomPlan(
                pair.tableNo(),
                pair.maleMemberId(),
                pair.femaleMemberId()
        );
    }
}
