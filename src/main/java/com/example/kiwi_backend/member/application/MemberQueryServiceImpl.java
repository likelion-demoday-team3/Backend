package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.MemberAdminSummary;
import com.example.kiwi_backend.contract.dto.MemberSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.entity.Profile;
import com.example.kiwi_backend.member.domain.enumerated.MemberStatus;
import com.example.kiwi_backend.member.domain.enumerated.Role;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import com.example.kiwi_backend.member.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** session 신청 시 성별, 활성 여부, 프로필 카드 작성 여부 확인용 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryServiceImpl implements MemberQueryService {

    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;

    @Override
    public MemberSummary getSummary(Long memberId) {
        return memberRepository.findById(memberId)
                .map(member -> new MemberSummary(member.getId(), member.getGender(), member.isActive(),
                        member.isIdentityVerified()))
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다"));
    }

    @Override
    public boolean hasCompletedProfile(Long memberId) {
        return profileRepository.findByMemberId(memberId)
                .map(Profile::isComplete)
                .orElse(false);
    }

    @Override
    public List<Long> findActiveAdminIds() {
        return memberRepository.findAllByRoleAndStatus(Role.ADMIN, MemberStatus.ACTIVE).stream()
                .map(Member::getId)
                .toList();
    }

    @Override
    public Map<Long, MemberAdminSummary> getAdminSummaries(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(memberIds).stream()
                .map(member -> new MemberAdminSummary(member.getId(), member.getName(), member.getEmail(),
                        member.getStatus().name()))
                .collect(Collectors.toMap(MemberAdminSummary::memberId, Function.identity()));
    }
}
