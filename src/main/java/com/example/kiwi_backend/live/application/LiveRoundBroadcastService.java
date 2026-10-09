package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.PartnerProfileCard;
import com.example.kiwi_backend.contract.dto.CompatibilitySummary;
import com.example.kiwi_backend.contract.query.CompatQueryService;
import com.example.kiwi_backend.contract.query.ProfileQueryService;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.entity.LiveRoundIdleParticipant;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveRoundBroadcastService {

    public static final String ROUND_ASSIGNED_DESTINATION = "/queue/live/round-assigned";
    public static final String ROUND_IDLE_DESTINATION = "/queue/live/round-idle";

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository;
    private final ProfileQueryService profileQueryService;
    private final ObjectProvider<CompatQueryService> compatQueryServiceProvider;
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

        List<LiveRoom> rooms = liveRoomRepository.findAllByRoundIdOrderByTableNoAsc(round.getId());
        if (rooms.isEmpty()) {
            throw new IllegalStateException("round has no rooms");
        }

        Set<Long> memberIds = new HashSet<>();
        for (LiveRoom room : rooms) {
            memberIds.add(room.getMaleMemberId());
            memberIds.add(room.getFemaleMemberId());
        }
        Map<Long, PartnerProfileCard> cards = profileQueryService.getCards(memberIds);
        if (cards == null || memberIds.stream().anyMatch(id -> cards.get(id) == null)) {
            throw new IllegalStateException("라운드 참가자 프로필 카드가 없습니다");
        }
        CompatQueryService compatQueryService = compatQueryServiceProvider.getIfAvailable();
        if (compatQueryService == null) {
            throw new IllegalStateException("CompatQueryService 구현체가 필요합니다");
        }
        Map<Long, CompatibilitySummary> compatibilityByRoomId = new HashMap<>();
        for (LiveRoom room : rooms) {
            CompatibilitySummary compatibility = compatQueryService.getCompatibility(
                    sessionId, room.getMaleMemberId(), room.getFemaleMemberId());
            if (compatibility == null) {
                throw new IllegalStateException("라운드 참가자 궁합 결과가 없습니다");
            }
            compatibilityByRoomId.put(room.getId(), compatibility);
        }

        int recipientCount = 0;
        for (LiveRoom room : rooms) {
            sendAssignment(round, room, room.getMaleMemberId(), room.getFemaleMemberId(),
                    cards.get(room.getFemaleMemberId()), compatibilityByRoomId.get(room.getId()));
            sendAssignment(round, room, room.getFemaleMemberId(), room.getMaleMemberId(),
                    cards.get(room.getMaleMemberId()), compatibilityByRoomId.get(room.getId()));
            recipientCount += 2;
        }

        return new LiveRoundBroadcastResult(sessionId, roundNumber, rooms.size(), recipientCount);
    }

    public void broadcastIdle(Long sessionId, int roundNumber, int breakSeconds) {
        LiveRound round = liveRoundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("round not found"));
        if (round.getId() == null) {
            throw new IllegalStateException("persisted round is required");
        }
        if (round.getStatus() != LiveRoundStatus.IN_PROGRESS) {
            throw new IllegalStateException("in-progress round만 대기 알림을 보낼 수 있습니다");
        }

        List<LiveRoundIdleParticipant> idleParticipants =
                liveRoundIdleParticipantRepository.findAllByRoundIdOrderByMemberIdAsc(round.getId());
        for (LiveRoundIdleParticipant idleParticipant : idleParticipants) {
            Long memberId = idleParticipant.getMemberId();
            if (memberId == null) {
                throw new IllegalArgumentException("idle memberId must not be null");
            }
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(),
                    ROUND_IDLE_DESTINATION,
                    new LiveRoundIdleMessage(
                            round.getSessionId(),
                            round.getId(),
                            round.getRoundNumber(),
                            memberId,
                            breakSeconds
                    )
            );
        }
    }

    private void sendAssignment(LiveRound round, LiveRoom room, Long memberId, Long partnerMemberId,
                                PartnerProfileCard partnerProfileCard, CompatibilitySummary compatibility) {
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
                        partnerMemberId,
                        partnerProfileCard,
                        compatibility
                )
        );
    }
}
