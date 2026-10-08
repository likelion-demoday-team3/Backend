package com.example.kiwi_backend.live.domain;

import com.example.kiwi_backend.contract.dto.Gender;

public record LiveParticipant(
//        참가자 한명에 대한 정보. 회원 번호와 성별
        Long memberId,
        Gender gender
) {
    public LiveParticipant {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }
        if (gender == null) {
            throw new IllegalArgumentException("gender must not be null");
        }
    }
}
