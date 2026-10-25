package com.example.kiwi_backend.vote.application;

import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.vote.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 마이페이지 최종 매칭 결과: 출석해 끝까지 진행한 세션마다 매칭 성공 여부와 상대 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchHistoryService {
    private final SessionQueryService sessions;
    private final MatchResultRepository matches;

    public List<Item> history(Long memberId) {
        var attended = sessions.findCompletedAttendances(memberId);
        if (attended.isEmpty()) return List.of();
        Map<Long, MatchResult> mine = matches.findAllBySessionIdIn(attended.stream().map(AttendedSession::sessionId).toList())
                .stream()
                .filter(m -> m.getMemberAId().equals(memberId) || m.getMemberBId().equals(memberId))
                .collect(Collectors.toMap(MatchResult::getSessionId, Function.identity(), (a, b) -> a));
        return attended.stream().map(session -> {
            var match = mine.get(session.sessionId());
            if (match == null) return new Item(session.sessionId(), session.startTime(), false, null, null, null);
            Long partnerId = match.getMemberAId().equals(memberId) ? match.getMemberBId() : match.getMemberAId();
            return new Item(session.sessionId(), session.startTime(), true, partnerId,
                    aliasOf(session.sessionId(), partnerId), match.getCreatedAt());
        }).toList();
    }

    private String aliasOf(Long sessionId, Long memberId) {
        return sessions.getLiveParticipants(sessionId).stream()
                .filter(p -> p.memberId().equals(memberId))
                .map(LiveParticipant::alias)
                .findFirst().orElse(null);
    }

    public record Item(Long sessionId, Instant sessionStartTime, boolean matched,
                       Long partnerMemberId, String partnerAlias, Instant matchedAt) { }
}
