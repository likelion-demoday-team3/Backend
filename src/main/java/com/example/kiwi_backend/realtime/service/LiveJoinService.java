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
import com.example.kiwi_backend.realtime.dto.LiveJoinPartner;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.LiveJoinStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveJoinService {

    /** 이미 만난 상대로 칠 라운드. PENDING 라운드는 방이 미리 짜여 있을 뿐 아직 만나지 않았다 */
    private static final List<LiveRoundStatus> MET_ROUND_STATUSES =
            List.of(LiveRoundStatus.IN_PROGRESS, LiveRoundStatus.COMPLETED);

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

        Map<Long, String> aliases = findAliases(sessionQueryService, sessionId);
        List<LiveJoinPartner> partners = findPartners(sessionId, memberId, aliases);

        return liveRoundRepository.findFirstBySessionIdAndStatusOrderByRoundNumberAsc(
                        sessionId,
                        LiveRoundStatus.IN_PROGRESS
                )
                .map(round -> restoreCurrentPosition(round, memberId, aliases, partners))
                .orElseGet(() -> joined(sessionId, memberId, partners));
    }

    private LiveJoinResponse restoreCurrentPosition(LiveRound round, Long memberId,
                                                    Map<Long, String> aliases, List<LiveJoinPartner> partners) {
        return liveRoomRepository.findByRoundIdAndMemberId(round.getId(), memberId)
                .map(room -> assigned(round, room, memberId, aliases, partners))
                .orElseGet(() -> idleOrJoined(round, memberId, partners));
    }

    private LiveJoinResponse idleOrJoined(LiveRound round, Long memberId, List<LiveJoinPartner> partners) {
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
                    null,
                    null,
                    partners
            );
        }
        return joined(round.getSessionId(), memberId, partners);
    }

    private LiveJoinResponse assigned(LiveRound round, LiveRoom room, Long memberId,
                                      Map<Long, String> aliases, List<LiveJoinPartner> partners) {
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
                aliases.get(partnerMemberId),
                profileQueryService.getCards(List.of(partnerMemberId)).get(partnerMemberId),
                round.getStartedAt().plusSeconds(sessionQueryServiceProvider.getIfAvailable()
                        .getRoundDurationSeconds(round.getSessionId())),
                partners
        );
    }

    private LiveJoinResponse joined(Long sessionId, Long memberId, List<LiveJoinPartner> partners) {
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
                null,
                null,
                partners
        );
    }

    /** 이미 시작했거나 끝난 라운드에서 만난 상대들 (라운드 순) */
    private List<LiveJoinPartner> findPartners(Long sessionId, Long memberId, Map<Long, String> aliases) {
        return liveRoomRepository.findAllBySessionIdAndMemberIdAndRoundStatusIn(
                        sessionId, memberId, MET_ROUND_STATUSES)
                .stream()
                .map(room -> {
                    Long partnerMemberId = findPartnerMemberId(room, memberId);
                    return new LiveJoinPartner(
                            partnerMemberId,
                            aliases.get(partnerMemberId),
                            room.getRound().getRoundNumber()
                    );
                })
                .toList();
    }

    /** 세션 안에서의 별칭 (memberId -> alias). 조회할 수 없으면 비어 있다 (화면 표시용이라 재접속을 막지 않는다) */
    private Map<Long, String> findAliases(SessionQueryService sessionQueryService, Long sessionId) {
        List<LiveParticipant> participants = sessionQueryService.getLiveParticipants(sessionId);
        Map<Long, String> aliases = new HashMap<>();
        if (participants == null) {
            return aliases;
        }
        for (LiveParticipant participant : participants) {
            if (participant.memberId() != null && participant.alias() != null) {
                aliases.putIfAbsent(participant.memberId(), participant.alias());
            }
        }
        return aliases;
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
