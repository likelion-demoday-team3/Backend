package com.example.kiwi_backend.payment.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.payment.api.dto.ShopProductResponse;
import com.example.kiwi_backend.payment.application.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 상점 페이지 */
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    /** 판매 중인 보석 상품 */
    @GetMapping("/products")
    public ApiResponse<List<ShopProductResponse>> products() {
        return ApiResponse.ok(shopService.getProducts());
    }
}
