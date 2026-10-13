package com.example.kiwi_backend.payment.application;

import com.example.kiwi_backend.payment.api.dto.ShopProductResponse;
import com.example.kiwi_backend.payment.domain.repository.ShopProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 상점 상품 조회 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ShopProductResponse> getProducts() {
        return productRepository.findByActiveTrueOrderByPriceAscIdAsc().stream()
                .map(ShopProductResponse::from)
                .toList();
    }
}
