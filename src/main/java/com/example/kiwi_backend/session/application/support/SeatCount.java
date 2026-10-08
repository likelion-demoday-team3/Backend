package com.example.kiwi_backend.session.application.support;

import com.example.kiwi_backend.contract.dto.Gender;

/** 한 세션의 성별 신청 인원과 남은 자리 */
public record SeatCount(int capacityPerGender, int maleApplied, int femaleApplied) {

    public int remaining(Gender gender) {
        int applied = gender == Gender.MALE ? maleApplied : femaleApplied;
        return Math.max(0, capacityPerGender - applied);
    }

    public int maleRemaining() {
        return remaining(Gender.MALE);
    }

    public int femaleRemaining() {
        return remaining(Gender.FEMALE);
    }
}
