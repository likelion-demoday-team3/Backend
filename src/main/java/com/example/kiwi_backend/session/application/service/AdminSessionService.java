package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.session.api.dto.SessionCreateRequest;
import com.example.kiwi_backend.session.api.dto.SessionDeleteResponse;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 운영자 세션 관리: 생성, 수정(모집 전만), 모집 시작, 삭제. 취소는 SessionDecisionService */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminSessionService {

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final SessionDecisionService sessionDecisionService;
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

    /**
     * 운영자 삭제. 로비 마감 전(SCHEDULED, RECRUITING, READY)까지만 가능.
     *
     * 신청 기록이 하나도 없으면 세션 행을 지운다. 세션을 참조하는 다른 테이블(라운드, 투표, 보석 기록 등)은
     * 모두 신청이나 진행이 있어야 생기므로 이때는 지울 것이 세션 하나뿐이다.
     * 신청 기록이 있으면 참가비·환불 기록이 세션을 가리키므로 지우지 않고, 운영자 취소(전원 100% 환불)로 대신한다.
     * 취소된 세션은 목록에 나오지 않아서 사용자에게는 삭제와 같다.
     */
    public SessionDeleteResponse delete(Long sessionId) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 세션입니다");
        }
        if (!session.isAdminCancellable()) {
            throw new IllegalStateException("로비 마감 전까지만 삭제할 수 있습니다");
        }

        if (!participantRepository.existsBySessionId(sessionId)) {
            sessionRepository.delete(session);
            return SessionDeleteResponse.deleted(sessionId);
        }
        int refunded = sessionDecisionService.cancelByAdmin(sessionId);
        return SessionDeleteResponse.cancelled(sessionId, refunded);
    }
}
