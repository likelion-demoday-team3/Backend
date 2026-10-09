package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.realtime.dto.SignalMessage;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
import com.example.kiwi_backend.realtime.dto.SignalType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RealtimeSignalServiceTest {

    private final LiveRoomRepository liveRoomRepository = mock(LiveRoomRepository.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

    private final RealtimeSignalService service = new RealtimeSignalService(
            liveRoomRepository,
            messagingTemplate
    );

    @Test
    void sendSignalSendsMessageToRoomPartner() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        SignalRequest request = new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp");

        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        service.sendSignal(request, 101L);

        ArgumentCaptor<SignalMessage> messageCaptor = ArgumentCaptor.forClass(SignalMessage.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("201"),
                org.mockito.ArgumentMatchers.eq(RealtimeSignalService.SIGNAL_DESTINATION),
                messageCaptor.capture()
        );

        assertThat(messageCaptor.getValue().sessionId()).isEqualTo(10L);
        assertThat(messageCaptor.getValue().roomId()).isEqualTo(100L);
        assertThat(messageCaptor.getValue().fromMemberId()).isEqualTo(101L);
        assertThat(messageCaptor.getValue().toMemberId()).isEqualTo(201L);
        assertThat(messageCaptor.getValue().type()).isEqualTo(SignalType.OFFER);
        assertThat(messageCaptor.getValue().payload()).isEqualTo("offer-sdp");
    }

    @Test
    void sendSignalRejectsSenderOutsideRoom() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        SignalRequest request = new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp");

        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.sendSignal(request, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sender is not in the room");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendSignalRejectsReceiverOutsideRoom() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        SignalRequest request = new SignalRequest(10L, 100L, 999L, SignalType.OFFER, "offer-sdp");

        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.sendSignal(request, 101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("receiver must be the room partner");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendSignalRejectsRoomInDifferentSession() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        SignalRequest request = new SignalRequest(99L, 100L, 201L, SignalType.OFFER, "offer-sdp");

        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.sendSignal(request, 101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("room does not belong to session");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendSignalRejectsPendingRound() {
        LiveRound round = persistedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        SignalRequest request = new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp");

        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.sendSignal(request, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("진행 중인 라운드에서만 signal을 보낼 수 있습니다");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendSignalSendsAnswerAndIceCandidateBackToRoomPartner() {
        LiveRound round = startedRound(10L, 1L, 1);
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        service.sendSignal(new SignalRequest(10L, 100L, 101L, SignalType.ANSWER, "answer-sdp"), 201L);
        service.sendSignal(new SignalRequest(10L, 100L, 101L, SignalType.ICE_CANDIDATE, "candidate"), 201L);

        verify(messagingTemplate).convertAndSendToUser(
                "101", RealtimeSignalService.SIGNAL_DESTINATION,
                new SignalMessage(10L, 100L, 201L, 101L, SignalType.ANSWER, "answer-sdp"));
        verify(messagingTemplate).convertAndSendToUser(
                "101", RealtimeSignalService.SIGNAL_DESTINATION,
                new SignalMessage(10L, 100L, 201L, 101L, SignalType.ICE_CANDIDATE, "candidate"));
    }

    @Test
    void sendSignalRejectsUnknownRoom() {
        when(liveRoomRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendSignal(
                new SignalRequest(10L, 100L, 201L, SignalType.OFFER, "offer-sdp"), 101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("room not found");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendSignalRejectsCompletedRound() {
        LiveRound round = startedRound(10L, 1L, 1);
        round.complete(Instant.parse("2026-10-07T09:03:00Z"));
        LiveRoom room = persistedRoom(100L, round, 1, 101L, 201L);
        when(liveRoomRepository.findById(100L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.sendSignal(
                new SignalRequest(10L, 100L, 201L, SignalType.ICE_CANDIDATE, "candidate"), 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("진행 중인 라운드에서만 signal을 보낼 수 있습니다");

        verifyNoInteractions(messagingTemplate);
    }

    private LiveRound persistedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = LiveRound.create(sessionId, roundNumber);
        ReflectionTestUtils.setField(round, "id", roundId);
        return round;
    }

    private LiveRound startedRound(Long sessionId, Long roundId, int roundNumber) {
        LiveRound round = persistedRound(sessionId, roundId, roundNumber);
        round.start(Instant.parse("2026-10-07T09:00:00Z"));
        return round;
    }

    private LiveRoom persistedRoom(Long roomId, LiveRound round, int tableNo, Long maleMemberId, Long femaleMemberId) {
        LiveRoom room = LiveRoom.create(round, tableNo, maleMemberId, femaleMemberId);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }
}
