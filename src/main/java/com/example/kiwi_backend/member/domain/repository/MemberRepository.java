package com.example.kiwi_backend.member.domain.repository;

import com.example.kiwi_backend.member.domain.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    Optional<Member> findByPublicId(UUID publicId);

    boolean existsByEmail(String email);

    boolean existsByPortoneIdentityVerificationId(String verificationId);
}
