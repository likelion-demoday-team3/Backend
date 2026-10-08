package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.entity.LiveRoundIdleParticipant;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveRoundBroadcastServiceTest {

    private final LiveRoundRepository liveRoundRepository = mock(LiveRoundRepository.class);
    private final LiveRoomRepository liveRoomRepository = mock(LiveRoomRepository.class);
    private final LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository =
            mock(LiveRoundIdleParticipantRepository.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

    private final LiveRoundBroadcastService service = new LiveRoundBroadcastService(
            liveRoundRepository,
            liveRoomRepository,
            liveRoundIdleParticipantRepository,
            messagingTemplate
    );

    @Test
    void broadcastRoundAssignmentsSendsMessageToBothParticipantsInEachRoom() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom firstRoom = persistedRoom(100L, round, 1, 101L, 201L);
        LiveRoom secondRoom = persistedRoom(101L, round, 2, 102L, 202L);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L))
                .thenReturn(List.of(firstRoom, secondRoom));

        LiveRoundBroadcastResult result = service.broadcastRoundAssignments(10L, 1);

        assertThat(result.sessionId()).isEqualTo(10L);
        assertThat(result.roundNumber()).isEqualTo(1);
        assertThat(result.roomCount()).isEqualTo(2);
        assertThat(result.recipientCount()).isEqualTo(4);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LiveRoundAssignmentMessage> messageCaptor =
                ArgumentCaptor.forClass(LiveRoundAssignmentMessage.class);

        verify(messagingTemplate, org.mockito.Mockito.times(4)).convertAndSendToUser(
                userCaptor.capture(),
                destinationCaptor.capture(),
                messageCaptor.capture()
        );

        assertThat(userCaptor.getAllValues()).containsExactly("101", "201", "102", "202");
        assertThat(destinationCaptor.getAllValues())
                .containsOnly(LiveRoundBroadcastService.ROUND_ASSIGNED_DESTINATION);
        assertThat(messageCaptor.getAllValues())
                .extracting(LiveRoundAssignmentMessage::partnerMemberId)
                .containsExactly(201L, 101L, 202L, 102L);
    }

    @Test
    void broadcastRoundAssignmentsRejectsRoundWithoutRooms() {
        LiveRound round = startedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));
        when(liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(1L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("round has no rooms");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastRoundAssignmentsRejectsPendingRound() {
        LiveRound round = persistedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.broadcastRoundAssignments(10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("in-progress round만 배정 알림을 보낼 수 있습니다");

        verifyNoInteractions(liveRoomRepository, messagingTemplate);
    }

    @Test
    void broadcastIdleSendsMessageToIdleParticipants() {
        LiveRound round = startedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));
        when(liveRoundIdleParticipantRepository.findAllByRoundIdOrderByMemberIdAsc(1L))
                .thenReturn(List.of(
                        LiveRoundIdleParticipant.create(round, 301L),
                        LiveRoundIdleParticipant.create(round, 302L)
                ));

        service.broadcastIdle(10L, 1, 60);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LiveRoundIdleMessage> messageCaptor =
                ArgumentCaptor.forClass(LiveRoundIdleMessage.class);

        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                userCaptor.capture(),
                destinationCaptor.capture(),
                messageCaptor.capture()
        );

        assertThat(userCaptor.getAllValues()).containsExactly("301", "302");
        assertThat(destinationCaptor.getAllValues())
                .containsOnly(LiveRoundBroadcastService.ROUND_IDLE_DESTINATION);
        assertThat(messageCaptor.getAllValues())
                .extracting(LiveRoundIdleMessage::breakSeconds)
                .containsExactly(60, 60);
    }

    @Test
    void broadcastIdleRejectsPendingRound() {
        LiveRound round = persistedRound(10L, 1L, 1);

        when(liveRoundRepository.findBySessionIdAndRoundNumber(10L, 1))
                .thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.broadcastIdle(10L, 1, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("in-progress round만 대기 알림을 보낼 수 있습니다");

        verifyNoInteractions(messagingTemplate);
    }

    private LiveRound persistedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = LiveRound.create(sessionId, roundNumber);
        ReflectionTestUtils.setField(round, "id", roundId);
        return round;
    }

    private LiveRound startedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = persistedRound(sessionId, roundId, roundNumber);
        round.start(Instant.parse("2026-10-05T09:00:00Z"));
        return round;
    }

    private LiveRoom persistedRoom(Long roomId, LiveRound round, int tableNo, Long maleMemberId, Long femaleMemberId) {
        LiveRoom room = LiveRoom.create(round, tableNo, maleMemberId, femaleMemberId);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }
}
