package com.example.kiwi_backend.vote.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "result_purchase", uniqueConstraints = @UniqueConstraint(name = "uk_result_purchase",
        columnNames = {"session_id", "buyer_id", "product", "target_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResultPurchase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long buyerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 30) private ResultProduct product;
    @Column(nullable = false, updatable = false) private Long targetKey;
    @Column(nullable = false, updatable = false) private int chargedGem;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    public static ResultPurchase create(Long sessionId, Long buyer, ResultProduct product, Long target, Instant now) {
        var purchase = new ResultPurchase();
        purchase.sessionId = sessionId; purchase.buyerId = buyer; purchase.product = product;
        purchase.targetKey = target; purchase.chargedGem = product.gems(); purchase.createdAt = now;
        return purchase;
    }
}
