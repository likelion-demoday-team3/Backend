package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.CompatibilitySummary;
import com.example.kiwi_backend.contract.query.CompatQueryService;
import com.example.kiwi_backend.live.domain.entity.SessionCompatibility;
import com.example.kiwi_backend.live.domain.repository.SessionCompatibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionCompatQueryService implements CompatQueryService {

    private final SessionCompatibilityRepository repository;

    @Override
    public CompatibilitySummary getCompatibility(Long sessionId, Long memberAId, Long memberBId) {
        if (sessionId == null || memberAId == null || memberBId == null || memberAId.equals(memberBId)) {
            throw new IllegalArgumentException("session and two distinct members are required");
        }
        long a = Math.min(memberAId, memberBId);
        long b = Math.max(memberAId, memberBId);
        SessionCompatibility result = repository.findBySessionIdAndMemberAIdAndMemberBId(sessionId, a, b)
                .orElseThrow(() -> new IllegalStateException("궁합 결과가 아직 준비되지 않았습니다"));
        return new CompatibilitySummary(result.getScore(), result.getReason());
    }
}
