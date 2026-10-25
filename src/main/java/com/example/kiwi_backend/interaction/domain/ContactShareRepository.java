package com.example.kiwi_backend.interaction.domain;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface ContactShareRepository extends JpaRepository<ContactShare, Long> {
    Optional<ContactShare> findBySessionIdAndSenderIdAndReceiverId(Long sessionId, Long senderId, Long receiverId);
    @Query("select s from ContactShare s where s.senderId = :memberId or s.receiverId = :memberId order by s.createdAt desc, s.id desc")
    Page<ContactShare> history(@Param("memberId") Long memberId, Pageable pageable);
    @Query("select s from ContactShare s where s.senderId = :memberId order by s.createdAt desc, s.id desc")
    Page<ContactShare> sentHistory(@Param("memberId") Long memberId, Pageable pageable);
    @Query("select s from ContactShare s where s.receiverId = :memberId order by s.createdAt desc, s.id desc")
    Page<ContactShare> receivedHistory(@Param("memberId") Long memberId, Pageable pageable);
}
