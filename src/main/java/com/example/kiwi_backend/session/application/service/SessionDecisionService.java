package com.example.kiwi_backend.session.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;
import com.example.kiwi_backend.session.domain.policy.ProceedPolicy;
import com.example.kiwi_backend.session.domain.repository.DatingSessionRepository;
import com.example.kiwi_backend.session.domain.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 세션 진행 여부 판단과 세션 취소.
 * 30분 전 판단은 스케줄러가, 운영자 취소는 운영자 API가 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionDecisionService {

    private final DatingSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final GemWallet gemWallet;

    /**
     * 시작 30분 전 판단. 진행 조건을 넘으면 READY, 못 넘으면 CANCELLED + 신청자 전원 100% 환불.
     * 두 번 실행돼도 안전하다: 이미 판단한 세션은 RECRUITING이 아니라서 건너뛴다.
     */
    @Transactional
    public void decide(Long sessionId, Instant now) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || !session.isDueForDecision(now)) {
            return;
        }

        List<SessionParticipant> applied =
                participantRepository.findAllBySessionIdAndStatus(sessionId, ParticipantStatus.APPLIED);
        int male = (int) applied.stream().filter(p -> p.getGender() == Gender.MALE).count();
        int female = applied.size() - male;

        if (ProceedPolicy.canProceed(male, female)) {
            session.markReady();
            log.info("세션 진행 확정 sessionId={} male={} female={}", sessionId, male, female);
        } else {
            cancelWithFullRefund(session, applied);
            log.info("세션 인원 미달 취소 sessionId={} male={} female={}", sessionId, male, female);
        }
    }

    /** 운영자 취소. 신청자 전원 100% 환불 */
    @Transactional
    public void cancelByAdmin(Long sessionId) {
        DatingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED) {
            throw new IllegalStateException("이미 끝났거나 취소된 세션입니다");
        }
        List<SessionParticipant> applied =
                participantRepository.findAllBySessionIdAndStatus(sessionId, ParticipantStatus.APPLIED);
        cancelWithFullRefund(session, applied);
    }

    /**
     * 세션 취소는 본인 취소가 아니므로 참가자 상태는 그대로 두고 환불 기록만 남긴다.
     * 환불 멱등 키가 신청 시각 기준이라, 본인 취소로 이미 환불받은 신청과 겹치지 않는다
     * (본인 취소한 사람은 APPLIED가 아니라서 애초에 대상이 아님).
     */
    private void cancelWithFullRefund(DatingSession session, List<SessionParticipant> applied) {
        session.cancel();
        int fee = session.getEntryFeeGem();
        if (fee <= 0) {
            return;
        }
        for (SessionParticipant participant : applied) {
            gemWallet.apply(GemCommand.entryFeeRefund(
                    participant.getMemberId(), session.getId(), fee, participant.getAppliedAt()));
        }
    }
}
