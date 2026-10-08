package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.contract.dto.MemberCredential;
import com.example.kiwi_backend.contract.query.MemberCredentialQuery;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** auth 모듈의 로그인 검증용 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberCredentialQueryImpl implements MemberCredentialQuery {

    private final MemberRepository memberRepository;

    @Override
    public Optional<MemberCredential> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return memberRepository.findByEmail(email.strip().toLowerCase())
                .map(member -> new MemberCredential(
                        member.getId(),
                        member.getPasswordHash(),
                        member.getRole().name(),
                        member.isActive()
                ));
    }
}

