package com.example.kiwi_backend.contract.query;
import java.util.Optional;
public interface MatchQueryService {
    Optional<Long> matchedPartner(Long sessionId, Long memberId);
}
