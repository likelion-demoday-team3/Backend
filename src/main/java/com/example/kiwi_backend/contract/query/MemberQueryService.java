package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.MemberSummary;

/** 회원 요약 조회. member가 구현하고 session 등이 사용한다 */
public interface MemberQueryService {

    /** 없는 회원이면 NotFoundException */
    MemberSummary getSummary(Long memberId);
}
