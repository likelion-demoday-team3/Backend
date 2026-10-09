package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.MatchPair;
import com.example.kiwi_backend.live.domain.RotationTable;
import com.example.kiwi_backend.live.domain.RotationTableGenerator;
import com.example.kiwi_backend.live.domain.RoundAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveSessionStartService {

    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider;
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

        List<com.example.kiwi_backend.live.domain.LiveParticipant> participants =
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
