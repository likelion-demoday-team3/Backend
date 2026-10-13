package com.example.kiwi_backend.contract.dto;
import java.util.List;
public record MatchingCompletedEvent(Long sessionId, List<MatchedPair> pairs) {
    public MatchingCompletedEvent { pairs = List.copyOf(pairs); }
}
