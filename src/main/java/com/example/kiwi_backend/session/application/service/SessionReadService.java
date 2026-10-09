package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.api.dto.MySessionResponse;
import com.example.kiwi_backend.session.api.dto.SessionDetailResponse;
import com.example.kiwi_backend.session.api.dto.SessionSummaryResponse;
import com.example.kiwi_backend.session.application.support.SeatCount;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.GenderCount;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** 세션 목록·상세·내 세션 조회 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionReadService {

    private static final List<SessionStatus> VISIBLE_STATUSES =
            List.of(SessionStatus.RECRUITING, SessionStatus.READY);

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;

    /** 아직 시작 전이고 모집 중이거나 진행 확정된 세션 */
    public List<SessionSummaryResponse> listUpcoming() {
        List<DatingSession> sessions = sessionRepository
                .findAllByStatusInAndStartTimeAfterOrderByStartTimeAsc(VISIBLE_STATUSES, Instant.now());
        Map<Long, SeatCount> seats = seatCounts(sessions);
        return sessions.stream()
                .map(session -> SessionSummaryResponse.of(session, seats.get(session.getId())))
                .toList();
    }

    public SessionDetailResponse detail(Long sessionId, Long memberId) {
        DatingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        SeatCount seats = seatCounts(List.of(session)).get(sessionId);
        SessionParticipant me = participantRepository.findBySessionIdAndMemberId(sessionId, memberId).orElse(null);
        return SessionDetailResponse.of(session, seats, me);
    }

    public List<MySessionResponse> mySessions(Long memberId) {
        List<SessionParticipant> participations = participantRepository.findAllByMemberIdOrderByAppliedAtDesc(memberId);
        Map<Long, DatingSession> sessions = sessionRepository
                .findAllById(participations.stream().map(SessionParticipant::getSessionId).toList())
                .stream()
                .collect(Collectors.toMap(DatingSession::getId, s -> s));

        return participations.stream()
                .filter(p -> sessions.containsKey(p.getSessionId()))
                .map(p -> {
                    DatingSession s = sessions.get(p.getSessionId());
                    return new MySessionResponse(s.getId(), s.getStartTime(), s.getStatus(),
                            p.getStatus(), p.getAlias(), p.getAppliedAt());
                })
                .toList();
    }

    /** 세션마다 따로 세지 않고 한 번의 쿼리로 성별 신청 인원을 센다 */
    private Map<Long, SeatCount> seatCounts(List<DatingSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = sessions.stream().map(DatingSession::getId).toList();
        Map<Long, Map<Gender, Long>> counts = new HashMap<>();
        for (GenderCount row : participantRepository.countByGender(ids, ParticipantStatus.APPLIED)) {
            counts.computeIfAbsent(row.getSessionId(), k -> new EnumMap<>(Gender.class))
                    .put(row.getGender(), row.getCount());
        }

        Map<Long, SeatCount> result = new HashMap<>();
        for (DatingSession session : sessions) {
            Map<Gender, Long> c = counts.getOrDefault(session.getId(), Map.of());
            result.put(session.getId(), new SeatCount(
                    session.getCapacityPerGender(),
                    c.getOrDefault(Gender.MALE, 0L).intValue(),
                    c.getOrDefault(Gender.FEMALE, 0L).intValue()));
        }
        return result;
    }
}
