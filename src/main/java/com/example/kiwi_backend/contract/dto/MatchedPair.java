package com.example.kiwi_backend.contract.dto;
public record MatchedPair(Long memberAId, Long memberBId) {
    public MatchedPair {
        if (memberAId == null || memberBId == null || memberAId >= memberBId) {
            throw new IllegalArgumentException("A match requires ascending distinct member IDs");
        }
    }
}
