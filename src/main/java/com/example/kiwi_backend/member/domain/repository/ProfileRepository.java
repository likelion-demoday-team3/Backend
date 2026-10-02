package com.example.kiwi_backend.member.domain.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    /** 프로필 카드 조회용. 관심사를 한 번에 가져와 N+1을 피한다 */
    @EntityGraph(attributePaths = "interests")
    Optional<Profile> findByMemberId(Long memberId);
}
