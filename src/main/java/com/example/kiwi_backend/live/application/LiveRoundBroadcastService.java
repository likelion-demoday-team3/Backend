package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveRoundBroadcastService {

    public static final String ROUND_ASSIGNED_DESTINATION = "/queue/live/round-assigned";

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public LiveRoundBroadcastResult broadcastRoundAssignments(Long sessionId, int roundNumber) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }

        LiveRound round = liveRoundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("round not found"));
        if (round.getId() == null) {
            throw new IllegalStateException("persisted round is required");
        }
        if (round.getStatus() != LiveRoundStatus.IN_PROGRESS) {
            throw new IllegalStateException("in-progress round만 배정 알림을 보낼 수 있습니다");
        }
//        아직 시작 전이거나 이미 끝난 라운드는 상대 배정 메시지를 보내지 않는다.

        List<LiveRoom> rooms = liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(round.getId());
        if (rooms.isEmpty()) {
            throw new IllegalStateException("round has no rooms");
        }

        int recipientCount = 0;
        for (LiveRoom room : rooms) {
            sendAssignment(round, room, room.getMaleMemberId(), room.getFemaleMemberId());
            sendAssignment(round, room, room.getFemaleMemberId(), room.getMaleMemberId());
            recipientCount += 2;
        }

        return new LiveRoundBroadcastResult(sessionId, roundNumber, rooms.size(), recipientCount);
    }

    private void sendAssignment(LiveRound round, LiveRoom room, Long memberId, Long partnerMemberId) {
        messagingTemplate.convertAndSendToUser(
                memberId.toString(),
                ROUND_ASSIGNED_DESTINATION,
                new LiveRoundAssignmentMessage(
                        round.getSessionId(),
                        round.getId(),
                        round.getRoundNumber(),
                        room.getId(),
                        room.getTableNo(),
                        memberId,
                        partnerMemberId
                )
        );
    }
}
