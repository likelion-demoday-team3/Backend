package com.example.kiwi_backend.realtime.service;

import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.query.ProfileQueryService;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundIdleParticipantRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.LiveJoinStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveJoinService {

    private final ObjectProvider<SessionQueryService> sessionQueryServiceProvider;
    private final LiveRoundRepository liveRoundRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final LiveRoundIdleParticipantRepository liveRoundIdleParticipantRepository;
    private final ProfileQueryService profileQueryService;

    public LiveJoinResponse join(Long sessionId, Long memberId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }

        // 구현체 가져오기
        SessionQueryService sessionQueryService = sessionQueryServiceProvider.getIfAvailable();
        if (sessionQueryService == null) {
            throw new IllegalStateException("SessionQueryService 구현체가 필요합니다");
        }
        // 이 회원이 지금 라이브(로비나 라운드)에 들어와도 되는가
        if (!sessionQueryService.isParticipant(sessionId, memberId)) {
            throw new IllegalStateException("입장 불가합니다");
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
                    null,
                    null,
                    null
            );
        }
        return joined(round.getSessionId(), memberId);
    }

    private LiveJoinResponse assigned(LiveRound round, LiveRoom room, Long memberId) {
        Long partnerMemberId = findPartnerMemberId(room, memberId);
        return new LiveJoinResponse(
                round.getSessionId(),
                memberId,
                LiveJoinStatus.ASSIGNED,
                round.getId(),
                round.getRoundNumber(),
                room.getId(),
                room.getTableNo(),
                partnerMemberId,
                findAlias(round.getSessionId(), partnerMemberId),
                profileQueryService.getCards(List.of(partnerMemberId)).get(partnerMemberId)
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
                null,
                null,
                null
        );
    }

    /** 세션 안에서의 상대 별칭. 조회할 수 없으면 null (화면 표시용이라 재접속을 막지 않는다) */
    private String findAlias(Long sessionId, Long memberId) {
        SessionQueryService sessionQueryService = sessionQueryServiceProvider.getIfAvailable();
        if (sessionQueryService == null) {
            return null;
        }
        List<LiveParticipant> participants = sessionQueryService.getLiveParticipants(sessionId);
        if (participants == null) {
            return null;
        }
        return participants.stream()
                .filter(participant -> participant.memberId().equals(memberId))
                .map(LiveParticipant::alias)
                .findFirst()
                .orElse(null);
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
