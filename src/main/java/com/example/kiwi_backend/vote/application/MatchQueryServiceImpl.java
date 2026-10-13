package com.example.kiwi_backend.vote.application;
import com.example.kiwi_backend.contract.query.MatchQueryService;
import com.example.kiwi_backend.vote.domain.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchQueryServiceImpl implements MatchQueryService {
    private final MatchResultRepository matches;
    public Optional<Long> matchedPartner(Long sessionId, Long memberId) {
        return matches.findAllBySessionId(sessionId).stream()
                .filter(m -> m.getMemberAId().equals(memberId) || m.getMemberBId().equals(memberId))
                .map(m -> m.getMemberAId().equals(memberId) ? m.getMemberBId() : m.getMemberAId()).findFirst();
    }
}
