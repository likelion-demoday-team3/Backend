package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.dto.LiveSessionContext;
import com.example.kiwi_backend.contract.query.LiveEncounterQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 세션에서 내가 대화한 상대 목록. 연락처 화면이 탭을 새로 열어도(브라우저를 닫았다 다시 와도)
 * 상대 목록과 내 memberId를 서버에서 다시 받을 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class SessionPartnerService {

    private final SessionLiveAccess access;
    private final LiveEncounterQuery encounters;

    @Transactional
    public Partners partners(Long sessionId, Long memberId) {
        LiveSessionContext context = access.lock(sessionId);
        context.requireParticipant(memberId);
        Map<Long, String> aliases = context.participants().stream()
                .filter(participant -> participant.alias() != null)
                .collect(Collectors.toMap(LiveParticipant::memberId, LiveParticipant::alias,
                        (first, ignored) -> first));
        List<Partner> partners = encounters.encounters(sessionId, memberId).stream()
                .map(encounter -> new Partner(encounter.partnerMemberId(),
                        aliases.get(encounter.partnerMemberId()), encounter.roundNumber()))
                .toList();
        return new Partners(sessionId, memberId, partners);
    }

    /** alias는 세션 안에서 보여줄 상대 별칭. 조회하지 못하면 null */
    public record Partner(Long memberId, String alias, int roundNumber) { }

    public record Partners(Long sessionId, Long myMemberId, List<Partner> partners) {
        public Partners {
            partners = List.copyOf(partners);
        }
    }
}
