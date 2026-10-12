package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.session.api.dto.SessionCreateRequest;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 운영자 세션 관리: 생성, 수정(모집 전만), 모집 시작. 취소는 SessionDecisionService */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminSessionService {

    private final DatingSessionRepository sessionRepository;
    private final Clock clock;

    public Long create(SessionCreateRequest request) {
        DatingSession session = DatingSession.create(
                request.startTime().toInstant(),
                request.capacityPerGender(),
                request.roundDurationSeconds(),
                request.votingDurationSeconds(),
                request.resultAndShareDurationSeconds(),
                request.entryFeeGem(),
                request.completionRefundGem(),
                clock.instant()
        );
        return sessionRepository.save(session).getId();
    }

    public void update(Long sessionId, SessionCreateRequest request) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        session.updateSettings(
                request.startTime().toInstant(),
                request.capacityPerGender(),
                request.roundDurationSeconds(),
                request.votingDurationSeconds(),
                request.resultAndShareDurationSeconds(),
                request.entryFeeGem(),
                request.completionRefundGem(),
                clock.instant()
        );
    }

    public void openRecruiting(Long sessionId) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        session.openRecruiting(clock.instant());
    }
}
