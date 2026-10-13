package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.MemberSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** session 신청 시 성별과 활성 여부 확인용 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryServiceImpl implements MemberQueryService {

    private final MemberRepository memberRepository;

    @Override
    public MemberSummary getSummary(Long memberId) {
        return memberRepository.findById(memberId)
                .map(member -> new MemberSummary(member.getId(), member.getGender(), member.isActive(),
                        member.isIdentityVerified()))
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다"));
    }
}
