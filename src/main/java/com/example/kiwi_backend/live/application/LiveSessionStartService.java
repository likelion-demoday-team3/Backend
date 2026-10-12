package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.command.SessionAttendanceCommand;
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

    @Transactional
    public LiveSessionStartResult startLiveSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }

        SessionQueryService sessionQueryService = sessionQueryServiceProvider.getIfAvailable();
        if (sessionQueryService == null) {
            throw new IllegalStateException("SessionQueryService 구현체가 필요합니다");
        }

        Instant startTime = sessionQueryService.getStartTime(sessionId);
        if (startTime == null) {
            throw new IllegalStateException("세션 시작 시각을 찾을 수 없습니다");
        }
        if (clock.instant().isBefore(startTime.plus(Duration.ofMinutes(1)))) {
            throw new IllegalStateException("로비 마감 시각 전에는 세션을 시작할 수 없습니다");
        }

        SessionAttendanceCommand attendanceCommand = sessionAttendanceCommandProvider.getIfAvailable();
        if (attendanceCommand == null) {
            throw new IllegalStateException("SessionAttendanceCommand 구현체가 필요합니다");
        }
        attendanceCommand.finalizeAttendance(sessionId, lobbyPresenceService.closeLobby(sessionId));

        List<LiveParticipant> participants =
                sessionQueryService.getLiveParticipants(sessionId);
//        참가자 명단 가져오기

        RotationTable rotationTable = rotationTableGenerator.generate(sessionId, participants);
        LiveScheduleResult scheduleResult = liveScheduleService.createSchedule(
                sessionId,
                toRoundPlans(rotationTable.rounds())
        );
        LiveRoundSchedulerResult schedulerResult = liveRoundSchedulerService.startSessionRounds(sessionId);
//        DB에 저장

        return new LiveSessionStartResult(
                sessionId,
                participants.size(),
                scheduleResult.roundCount(),
                scheduleResult.roomCount(),
                scheduleResult.idleCount(),
                schedulerResult.firstRoundNumber(),
                schedulerResult.roundDurationSeconds()
        );
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
