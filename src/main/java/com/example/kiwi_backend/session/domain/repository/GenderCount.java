package com.example.kiwi_backend.session.domain.repository;

import com.example.kiwi_backend.contract.dto.Gender;

/** 세션별·성별 신청 인원 집계 결과 */
public interface GenderCount {

    Long getSessionId();

    Gender getGender();

    long getCount();
}
