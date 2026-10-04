package com.example.kiwi_backend.live.domain;

public record MatchPair(
//        이번 라운드의 한 테이블의 1대1 매칭 정보
//        예시 “이번 라운드 2번 테이블에는 7번 남자와 13번 여자를 넣는다"
        int tableNo,
        Long maleMemberId,
        Long femaleMemberId
) {
    public MatchPair {
        if (tableNo < 1) {
            throw new IllegalArgumentException("tableNo must be positive");
        }
        if (maleMemberId == null) {
            throw new IllegalArgumentException("maleMemberId must not be null");
        }
        if (femaleMemberId == null) {
            throw new IllegalArgumentException("femaleMemberId must not be null");
        }
    }
}
