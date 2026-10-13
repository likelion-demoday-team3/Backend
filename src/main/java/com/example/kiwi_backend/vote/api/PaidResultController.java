package com.example.kiwi_backend.vote.api;
import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.vote.application.PaidResultService;
import com.example.kiwi_backend.vote.domain.ResultProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions/{sessionId}/results")
public class PaidResultController {
    private final PaidResultService service;
    @GetMapping("/products")
    public ApiResponse<List<PaidResultService.ProductInfo>> products(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.catalog(sessionId, user.memberId()));
    }
    @PostMapping("/purchases")
    public ApiResponse<PaidResultService.PurchaseResult> buy(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody Purchase request) {
        return ApiResponse.ok(service.purchase(sessionId, user.memberId(), request.product(), request.targetId()));
    }
    @GetMapping("/items")
    public ApiResponse<PaidResultService.Details> get(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user,
            @RequestParam ResultProduct product, @RequestParam(required = false) Long targetId) {
        return ApiResponse.ok(service.get(sessionId, user.memberId(), product, targetId));
    }
    public record Purchase(@NotNull ResultProduct product, Long targetId) { }
}
