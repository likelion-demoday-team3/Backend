package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.MemberAccountCommand;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 이용 정지·해제 */
@Service
@RequiredArgsConstructor
public class MemberAccountCommandImpl implements MemberAccountCommand {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void suspend(Long memberId) {
        Member member = find(memberId);
        if (member.isAdmin()) {
            throw new IllegalStateException("운영자 계정은 정지할 수 없습니다");
        }
        member.suspend();
    }

    @Override
    @Transactional
    public void reactivate(Long memberId) {
        find(memberId).reactivate();
    }

    private Member find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다"));
    }
}
