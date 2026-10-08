package com.example.kiwi_backend.wallet.domain.repository;

import com.example.kiwi_backend.wallet.domain.entity.WalletHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletHistoryRepository extends JpaRepository<WalletHistory, Long> {

    Optional<WalletHistory> findByIdempotencyKey(String idempotencyKey);

    /** 최신순. 같은 시각이면 id가 큰 것(나중에 기록된 것)이 먼저 */
    Page<WalletHistory> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);
}
