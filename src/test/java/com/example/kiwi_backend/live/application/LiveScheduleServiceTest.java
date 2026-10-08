package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LiveScheduleServiceTest {

    @Autowired
    private LiveScheduleService liveScheduleService;

    @Autowired
    private LiveRoundRepository liveRoundRepository;

    @Autowired
    private LiveRoomRepository liveRoomRepository;

    @Autowired
    private LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository;

    @Test
    void createScheduleStoresRoundsRoomsAndIdleParticipants() {
        LiveScheduleResult result = liveScheduleService.createSchedule(
                1L,
                List.of(
                        new LiveRoundPlan(1, List.of(
                                new LiveRoomPlan(1, 101L, 201L),
                                new LiveRoomPlan(2, 102L, 202L)
                        ), List.of(301L)),
                        new LiveRoundPlan(2, List.of(
                                new LiveRoomPlan(1, 101L, 202L),
                                new LiveRoomPlan(2, 102L, 201L)
                        ))
                )
        );

        assertThat(result.sessionId()).isEqualTo(1L);
        assertThat(result.roundCount()).isEqualTo(2);
        assertThat(result.roomCount()).isEqualTo(4);
        assertThat(result.idleCount()).isEqualTo(1);

        List<LiveRound> rounds = liveRoundRepository.findAllBySessionIdOrderByRoundNumberAsc(1L);
        assertThat(rounds).hasSize(2);
        assertThat(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(rounds.get(0).getId()))
                .hasSize(2);
        assertThat(liveRoundIdleParticipantRepository.findAllByRoundIdOrderByMemberIdAsc(rounds.get(0).getId()))
                .extracting("memberId")
                .containsExactly(301L);
    }

    @Test
    void createScheduleRejectsDuplicateTableNumberInSameRound() {
        List<LiveRoundPlan> plans = List.of(
                new LiveRoundPlan(1, List.of(
                        new LiveRoomPlan(1, 101L, 201L),
                        new LiveRoomPlan(1, 102L, 202L)
                ))
        );

        assertThatThrownBy(() -> liveScheduleService.createSchedule(1L, plans))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate tableNo is not allowed in a round");
    }

    @Test
    void createScheduleRejectsIdleMemberAssignedToRoomInSameRound() {
        List<LiveRoundPlan> plans = List.of(
                new LiveRoundPlan(1, List.of(
                        new LiveRoomPlan(1, 101L, 201L)
                ), List.of(101L))
        );

        assertThatThrownBy(() -> liveScheduleService.createSchedule(1L, plans))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idle member must not be assigned to a room in the same round");
    }
}
