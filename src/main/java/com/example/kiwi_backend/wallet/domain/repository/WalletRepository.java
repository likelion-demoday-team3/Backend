package com.example.kiwi_backend.wallet.domain.repository;

import com.example.kiwi_backend.wallet.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    /**
     * 보석을 바꾸기 전에 지갑 행을 잠근다 (SELECT ... FOR UPDATE).
     * 같은 회원에 대한 보석 변동은 한 번에 하나씩 순서대로 처리되고,
     * 다른 트랜잭션은 앞의 트랜잭션이 끝날 때까지 기다린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.memberId = :memberId")
    Optional<Wallet> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
