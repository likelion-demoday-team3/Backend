package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.LiveStatus;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** A(realtime·live)가 쓰는 세션 조회 창구 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryServiceImpl implements SessionQueryService {

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final Clock clock;

    /**
     * 라이브 입장 가능 여부. 세션 상태 · 로비 시간 · 참가자 상태를 함께 본다.
     *
     * 참고: 로비 마감(시작 + 1분) 직후 ~ 스케줄러가 출석을 확정하기 전(최대 5초) 사이에는
     * 세션이 아직 READY인데 로비 시간이 지나서 false가 된다. 이 몇 초 동안 끊겼다 다시 들어오는 사람은
     * 거부되고 노쇼로 잡힐 수 있다. 드문 경우라 지금은 그대로 둔다.
     */
    @Override
    public boolean isParticipant(Long sessionId, Long memberId) {
        DatingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return false;
        }
        SessionParticipant participant = participantRepository.findBySessionIdAndMemberId(sessionId, memberId)
                .orElse(null);
        if (participant == null) {
            return false;
        }
        Instant now = clock.instant();
        return switch (session.getStatus()) {
            // 세션이 READY(참가 마감 19:30 ~ 로비 마감 20:01 사이)인 경우 참가자가 APPLIED이고 로비가 open일 경우 TRUE
            case READY -> participant.getStatus() == ParticipantStatus.APPLIED && session.isLobbyOpen(now);
            // 세션이 IN_PROGRESS(로비 끝, 세션 시작)인 경우 참가자가 ATTENDED일 경우 TRUE
            case IN_PROGRESS -> participant.getStatus() == ParticipantStatus.ATTENDED;
            default -> false;
        };
    }

    @Override
    public Instant getStartTime(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .map(DatingSession::getStartTime)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
    }

    // 로비를 마감할 세션이 있는지 조회한다
    @Override
    public List<Long> findSessionIdsDueForLobbyClose(Instant now) {
        return sessionRepository.findIdsByStatusAndStartTimeAtOrBefore(
                SessionStatus.READY, now.minus(DatingSession.LOBBY_CLOSE_AFTER));
    }

    // 1라운드를 시작할 세션이 있는지 조회한다
    @Override
    public List<Long> findSessionIdsDueForLobbyClose(Instant now) {
        return sessionRepository.findIdsDueForFirstRound(SessionStatus.IN_PROGRESS, LiveStatus.WAITING, now);
    }

    // 해당 세션에서 ATTENDED인 참석자만 반환. 참석자의 (memberId, gender, alias)를 LiveParticipant로 반환
    @Override
    public List<LiveParticipant> getLiveParticipants(Long sessionId) {
        return participantRepository.findAllBySessionIdAndStatus(sessionId, ParticipantStatus.ATTENDED).stream()
                .map(p -> new LiveParticipant(p.getMemberId(), p.getGender(), p.getAlias()))
                .toList();
    }
}
