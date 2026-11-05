package com.example.kiwi_backend.member.domain.repository;

import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.enumerated.MemberStatus;
import com.example.kiwi_backend.member.domain.enumerated.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    List<Member> findAllByRoleAndStatus(Role role, MemberStatus status);

    Optional<Member> findByPublicId(UUID publicId);

    boolean existsByEmail(String email);

    boolean existsByPortoneIdentityVerificationId(String verificationId);
}
