package com.example.kiwi_backend.vote.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ResultPurchaseRepository extends JpaRepository<ResultPurchase, Long> {
    List<ResultPurchase> findAllBySessionIdAndBuyerId(Long sessionId, Long buyerId);
}
