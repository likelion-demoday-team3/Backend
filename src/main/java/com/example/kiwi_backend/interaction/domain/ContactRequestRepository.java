package com.example.kiwi_backend.interaction.domain;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    Optional<ContactRequest> findBySessionIdAndRequesterIdAndTargetId(Long sessionId, Long requesterId, Long targetId);
    @Query("select r from ContactRequest r where r.requesterId = :memberId or r.targetId = :memberId order by r.createdAt desc, r.id desc")
    Page<ContactRequest> history(@Param("memberId") Long memberId, Pageable pageable);
}
