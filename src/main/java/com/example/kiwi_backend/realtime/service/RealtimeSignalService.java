package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.realtime.dto.SignalMessage;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RealtimeSignalService {

    public static final String SIGNAL_DESTINATION = "/queue/live/signal";

    private final LiveRoomRepository liveRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void sendSignal(SignalRequest request, Long fromMemberId) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (fromMemberId == null) {
            throw new IllegalArgumentException("fromMemberId must not be null");
        }

        LiveRoom room = liveRoomRepository.findById(request.roomId())
                .orElseThrow(() -> new IllegalArgumentException("room not found"));

        validateRoom(request, fromMemberId, room);

        messagingTemplate.convertAndSendToUser(
                request.toMemberId().toString(),
                SIGNAL_DESTINATION,
                new SignalMessage(
                        request.sessionId(),
                        room.getId(),
                        fromMemberId,
                        request.toMemberId(),
                        request.type(),
                        request.payload()
                )
        );
    }

    private void validateRoom(SignalRequest request, Long fromMemberId, LiveRoom room) {
        if (room.getRound() == null) {
            throw new IllegalStateException("room round is required");
        }
        if (!room.getRound().getSessionId().equals(request.sessionId())) {
            throw new IllegalArgumentException("room does not belong to session");
        }
        if (room.getRound().getStatus() != LiveRoundStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 라운드에서만 signal을 보낼 수 있습니다");
        }
        if (fromMemberId.equals(request.toMemberId())) {
            throw new IllegalArgumentException("cannot send signal to self");
        }

        Long expectedReceiver = findPartnerMemberId(room, fromMemberId);
        if (!expectedReceiver.equals(request.toMemberId())) {
            throw new IllegalArgumentException("receiver must be the room partner");
        }
    }

    private Long findPartnerMemberId(LiveRoom room, Long fromMemberId) {
        if (fromMemberId.equals(room.getMaleMemberId())) {
            return room.getFemaleMemberId();
        }
        if (fromMemberId.equals(room.getFemaleMemberId())) {
            return room.getMaleMemberId();
        }
        throw new IllegalArgumentException("sender is not in the room");
    }
}
