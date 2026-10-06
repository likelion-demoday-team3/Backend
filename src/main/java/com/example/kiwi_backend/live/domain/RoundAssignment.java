package com.example.kiwi_backend.live.domain;

import java.util.List;

public record RoundAssignment(
//        한 라운드 전체
        int roundNumber,
        List<MatchPair> pairs,
        List<Long> idleMemberIds
//        남, 녀 수가 맞지 않을 때 쉬는 사람을 idleMemberIds에 넣는다. 쉬는 사람이 없으면 빈 리스트가 된다.
) {
    public RoundAssignment {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        pairs = List.copyOf(pairs);
        idleMemberIds = List.copyOf(idleMemberIds);
    }
}
