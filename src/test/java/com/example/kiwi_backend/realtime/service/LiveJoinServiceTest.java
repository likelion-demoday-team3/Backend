package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.LiveJoinStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveJoinServiceTest {

    private final LiveRoundRepository liveRoundRepository = mock(LiveRoundRepository.class);
    private final LiveRoomRepository liveRoomRepository = mock(LiveRoomRepository.class);
    private final LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository =
            mock(LiveRoundIdleParticipantRepository.class);

    private final LiveJoinService service = new LiveJoinService(
            liveRoundRepository,
            liveRoomRepository,
            liveRoundIdleParticipantRepository
    );

    @Test
    void joinReturnsJoinedWhenNoRoundIsInProgress() {
        when(liveRoundRepository.findFirstBySessionIdAndStatusOrderByRoundNumberAsc(
                10L,
                LiveRoundStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());

        LiveJoinResponse response = service.join(10L, 101L);

        assertThat(response.status()).isEqualTo(LiveJoinStatus.JOINED);
        assertThat(response.sessionId()).isEqualTo(10L);
        assertThat(response.memberId()).isEqualTo(101L);
        assertThat(response.roundId()).isNull();
        assertThat(response.roomId()).isNull();
    }

    @Test
    void joinRestoresCurrentRoomAssignment() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 2, 101L, 201L);

        when(liveRoundRepository.findFirstBySessionIdAndStatusOrderByRoundNumberAsc(
                10L,
                LiveRoundStatus.IN_PROGRESS
        )).thenReturn(Optional.of(round));
        when(liveRoomRepository.findByRoundIdAndMemberId(1L, 101L))
                .thenReturn(Optional.of(room));

        LiveJoinResponse response = service.join(10L, 101L);

        assertThat(response.status()).isEqualTo(LiveJoinStatus.ASSIGNED);
        assertThat(response.roundId()).isEqualTo(1L);
        assertThat(response.roundNumber()).isEqualTo(1);
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.tableNo()).isEqualTo(2);
        assertThat(response.partnerMemberId()).isEqualTo(201L);
    }

    @Test
    void joinRestoresCurrentIdleState() {
        LiveRound round = startedRound(10L, 1L, 1);

        when(liveRoundRepository.findFirstBySessionIdAndStatusOrderByRoundNumberAsc(
                10L,
                LiveRoundStatus.IN_PROGRESS
        )).thenReturn(Optional.of(round));
        when(liveRoomRepository.findByRoundIdAndMemberId(1L, 301L))
                .thenReturn(Optional.empty());
        when(liveRoundIdleParticipantRepository.existsByRoundIdAndMemberId(1L, 301L))
                .thenReturn(true);

        LiveJoinResponse response = service.join(10L, 301L);

        assertThat(response.status()).isEqualTo(LiveJoinStatus.IDLE);
        assertThat(response.roundId()).isEqualTo(1L);
        assertThat(response.roundNumber()).isEqualTo(1);
        assertThat(response.roomId()).isNull();
        assertThat(response.partnerMemberId()).isNull();
    }

    private LiveRound startedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = LiveRound.create(sessionId, roundNumber);
        ReflectionTestUtils.setField(round, "id", roundId);
        round.start(Instant.parse("2026-10-07T09:00:00Z"));
        return round;
    }

    private LiveRoom persistedRoom(Long roomId, LiveRound round, int tableNo, Long maleMemberId, Long femaleMemberId) {
        LiveRoom room = LiveRoom.create(round, tableNo, maleMemberId, femaleMemberId);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }
}
