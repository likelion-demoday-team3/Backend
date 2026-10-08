package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.member.api.dto.MyInfoResponse;
import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 본인 계정 관리: 내 정보 조회, 연락처 수정·삭제, 비밀번호 변경, 탈퇴.
 * 규칙 검증은 Member 엔티티가 하고, 여기서는 조회·흐름·트랜잭션만 담당한다.
 * 수정 메서드는 save()를 부르지 않는다. 트랜잭션이 끝날 때 변경 감지로 UPDATE된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long memberId) {
        return MyInfoResponse.from(findMember(memberId));
    }

    public void changePhoneNumber(Long memberId, String phoneNumber) {
        findMember(memberId).changePhoneNumber(phoneNumber);
    }

    public void removePhoneNumber(Long memberId) {
        findMember(memberId).changePhoneNumber(null);
    }

    public void changeInstagramId(Long memberId, String instagramId) {
        findMember(memberId).changeInstagramId(instagramId);
    }

    public void removeInstagramId(Long memberId) {
        findMember(memberId).changeInstagramId(null);
    }

    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = findMember(memberId);
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다");
        }
        member.changePassword(passwordEncoder.encode(newPassword));
    }

    /**
     * 탈퇴. 연락처는 엔티티에서 즉시 지워진다.
     * TODO: 신청해 둔 세션이 있으면 취소·환불 처리가 필요하다 (session 모듈과 계약 추가 후 연결)
     */
    public void withdraw(Long memberId) {
        findMember(memberId).withdraw();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다"));
    }
}
