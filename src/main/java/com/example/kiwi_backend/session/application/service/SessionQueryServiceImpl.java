package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A(realtime·live)가 쓰는 세션 조회 창구 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryServiceImpl implements SessionQueryService {

    private final SessionParticipantRepository participantRepository;

    @Override
    public boolean isParticipant(Long sessionId, Long memberId) {
        return participantRepository.findBySessionIdAndMemberId(sessionId, memberId)
                .map(SessionParticipant::isActive)
                .orElse(false);
    }
}
