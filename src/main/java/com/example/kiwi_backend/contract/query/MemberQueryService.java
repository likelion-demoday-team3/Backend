package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.MemberAdminSummary;
import com.example.kiwi_backend.contract.dto.MemberSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 회원 요약 조회. member가 구현하고 session 등이 사용한다 */
public interface MemberQueryService {

    /** 없는 회원이면 NotFoundException */
    MemberSummary getSummary(Long memberId);

    /** 세션 신청에 필요한 프로필 카드를 다 채웠는가 (문장 4개 + 관심사 1개 이상). 프로필이 없으면 false */
    boolean hasCompletedProfile(Long memberId);

    /** 신고 알림을 받을 운영자들 (활성 ADMIN 계정) */
    List<Long> findActiveAdminIds();

    /** 운영자 화면용 회원 정보. 없는 회원은 결과에서 빠진다 */
    Map<Long, MemberAdminSummary> getAdminSummaries(Collection<Long> memberIds);
}
