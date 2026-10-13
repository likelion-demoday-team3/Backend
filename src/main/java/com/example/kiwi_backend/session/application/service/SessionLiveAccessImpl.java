package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionLiveAccessImpl implements SessionLiveAccess {
    private final DatingSessionRepository sessions;
    private final SessionParticipantRepository participants;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LiveSessionContext lock(Long sessionId) {
        DatingSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
        if (session.getStatus() != SessionStatus.IN_PROGRESS && session.getStatus() != SessionStatus.COMPLETED) {
            throw new IllegalStateException("Session is not live");
        }
        var attendees = participants.findAllBySessionIdAndStatus(sessionId, ParticipantStatus.ATTENDED)
                .stream().map(p -> new LiveParticipant(p.getMemberId(), p.getGender(), p.getAlias())).toList();
        return new LiveSessionContext(sessionId, LiveSessionContext.Phase.valueOf(session.getLiveStatus().name()),
                session.getPhaseEndsAt(), session.getCompletionRefundGem(), attendees);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant startHearts(Long sessionId, Instant now) {
        return sessions.findByIdForUpdate(sessionId).orElseThrow().startHearts(now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findHeartSessionsDue(Instant now) {
        return sessions.findIdsDueForFirstRound(SessionStatus.IN_PROGRESS, LiveStatus.HEART, now);
    }
}
