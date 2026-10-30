package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.MemberSummary;

/** 회원 요약 조회. member가 구현하고 session 등이 사용한다 */
public interface MemberQueryService {

    /** 없는 회원이면 NotFoundException */
    MemberSummary getSummary(Long memberId);

    /** 세션 신청에 필요한 프로필 카드를 다 채웠는가 (문장 4개 + 관심사 1개 이상). 프로필이 없으면 false */
    boolean hasCompletedProfile(Long memberId);
}
