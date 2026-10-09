package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.LiveJoinStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveJoinService {

    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository;

    public LiveJoinResponse join(Long sessionId, Long memberId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }

        return liveRoundRepository.findFirstBySessionIdAndStatusOrderByRoundNumberAsc(
                        sessionId,
                        LiveRoundStatus.IN_PROGRESS
                )
                .map(round -> restoreCurrentPosition(round, memberId))
                .orElseGet(() -> joined(sessionId, memberId));
    }

    private LiveJoinResponse restoreCurrentPosition(LiveRound round, Long memberId) {
        return liveRoomRepository.findByRoundIdAndMemberId(round.getId(), memberId)
                .map(room -> assigned(round, room, memberId))
                .orElseGet(() -> idleOrJoined(round, memberId));
    }

    private LiveJoinResponse idleOrJoined(LiveRound round, Long memberId) {
        if (liveRoundIdleParticipantRepository.existsByRoundIdAndMemberId(round.getId(), memberId)) {
            return new LiveJoinResponse(
                    round.getSessionId(),
                    memberId,
                    LiveJoinStatus.IDLE,
                    round.getId(),
                    round.getRoundNumber(),
                    null,
                    null,
                    null
            );
        }
        return joined(round.getSessionId(), memberId);
    }

    private LiveJoinResponse assigned(LiveRound round, LiveRoom room, Long memberId) {
        return new LiveJoinResponse(
                round.getSessionId(),
                memberId,
                LiveJoinStatus.ASSIGNED,
                round.getId(),
                round.getRoundNumber(),
                room.getId(),
                room.getTableNo(),
                findPartnerMemberId(room, memberId)
        );
    }

    private LiveJoinResponse joined(Long sessionId, Long memberId) {
        return new LiveJoinResponse(
                sessionId,
                memberId,
                LiveJoinStatus.JOINED,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Long findPartnerMemberId(LiveRoom room, Long memberId) {
        if (memberId.equals(room.getMaleMemberId())) {
            return room.getFemaleMemberId();
        }
        if (memberId.equals(room.getFemaleMemberId())) {
            return room.getMaleMemberId();
        }
        throw new IllegalArgumentException("member is not in the room");
    }
}
