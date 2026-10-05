package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LiveScheduleService {
//    1. 이미 이 세션에 스케줄이 있는지 확인
//    2. 라운드 번호 중복 검사
//    3. 같은 라운드 안에서 tableNo 중복 검사
//    4. 같은 라운드 안에서 같은 남자/여자가 두 방에 들어가는지 검사
//    5. LiveRound 저장
//    6. LiveRoom 저장

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoomRepository liveRoomRepository;

    @Transactional
    public LiveScheduleResult createSchedule(Long sessionId, List<LiveRoundPlan> roundPlans) {
//        라운드와 방을 DB에 저장한다
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (roundPlans == null || roundPlans.isEmpty()) {
            throw new IllegalArgumentException("roundPlans must not be empty");
        }
        if (liveRoundRepository.existsBySessionId(sessionId)) {
            throw new IllegalStateException("이미 생성된 라이브 스케줄입니다");
        }

        validateRoundPlans(roundPlans);

        int roomCount = 0;
        for (LiveRoundPlan roundPlan : roundPlans) {
            LiveRound round = liveRoundRepository.save(LiveRound.create(sessionId, roundPlan.roundNumber()));
//            저장 후 round에는 DB가 만든 id가 들어간다

            List<LiveRoom> rooms = new ArrayList<>();
            for (LiveRoomPlan roomPlan : roundPlan.rooms()) {
                rooms.add(LiveRoom.create(
                        round,
                        roomPlan.tableNo(),
                        roomPlan.maleMemberId(),
                        roomPlan.femaleMemberId()
                ));
            }
            liveRoomRepository.saveAll(rooms);
            roomCount += rooms.size();
        }

        return new LiveScheduleResult(sessionId, roundPlans.size(), roomCount);
    }

    private void validateRoundPlans(List<LiveRoundPlan> roundPlans) {
        Set<Integer> roundNumbers = new HashSet<>();
        for (LiveRoundPlan roundPlan : roundPlans) {
            if (roundPlan == null) {
                throw new IllegalArgumentException("roundPlan must not be null");
            }
            if (!roundNumbers.add(roundPlan.roundNumber())) {
                throw new IllegalArgumentException("duplicate roundNumber is not allowed");
            }
            if (roundPlan.rooms().isEmpty()) {
                throw new IllegalArgumentException("rooms must not be empty");
            }

            Set<Integer> tableNumbers = new HashSet<>();
            Set<Long> maleMemberIds = new HashSet<>();
            Set<Long> femaleMemberIds = new HashSet<>();
            for (LiveRoomPlan roomPlan : roundPlan.rooms()) {
                if (roomPlan == null) {
                    throw new IllegalArgumentException("roomPlan must not be null");
                }
                if (!tableNumbers.add(roomPlan.tableNo())) {
                    throw new IllegalArgumentException("duplicate tableNo is not allowed in a round");
                }
                if (!maleMemberIds.add(roomPlan.maleMemberId())) {
                    throw new IllegalArgumentException("duplicate maleMemberId is not allowed in a round");
                }
                if (!femaleMemberIds.add(roomPlan.femaleMemberId())) {
                    throw new IllegalArgumentException("duplicate femaleMemberId is not allowed in a round");
                }
            }
        }
    }
}
