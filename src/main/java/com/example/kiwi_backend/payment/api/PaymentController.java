package com.example.kiwi_backend.payment.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.payment.api.dto.*;
import com.example.kiwi_backend.payment.application.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 보석 구매 결제 (토스페이먼츠).
 *
 * 1. POST /payments/toss/ready     → 받은 값으로 프론트가 토스 SDK 결제창을 연다
 * 2. 결제창에서 인증 → 토스가 프론트 successUrl?paymentKey=...&orderId=...&amount=... 으로 돌려보냄
 * 3. POST /payments/toss/confirm   → 승인 후 보석 지급
 *    (결제창에서 취소·실패로 failUrl에 돌아오면 POST /payments/{orderId}/abort)
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 결제 준비 */
    @PostMapping("/toss/ready")
    public ApiResponse<PaymentReadyResponse> ready(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody PaymentReadyRequest request) {
        return ApiResponse.ok(paymentService.ready(authUser.memberId(), request.productId()));
    }

    /** 결제 승인 (보석 지급) */
    @PostMapping("/toss/confirm")
    public ApiResponse<PaymentApproveResponse> approve(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody PaymentApproveRequest request) {
        return ApiResponse.ok(paymentService.approve(
                authUser.memberId(), request.orderId(), request.paymentKey(), request.amount()));
    }

    /** 결제창에서 취소·실패 */
    @PostMapping("/{orderId}/abort")
    public ApiResponse<Void> abort(@AuthenticationPrincipal AuthUser authUser,
                                   @PathVariable String orderId) {
        paymentService.abort(authUser.memberId(), orderId);
        return ApiResponse.ok();
    }

    /** 환불 (보석 회수 후 결제 취소) */
    @PostMapping("/{orderId}/refund")
    public ApiResponse<PaymentRefundResponse> refund(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable String orderId) {
        return ApiResponse.ok(paymentService.refund(authUser.memberId(), orderId));
    }

    /** 내 결제 내역 */
    @GetMapping("/my")
    public ApiResponse<List<PaymentHistoryResponse>> myPayments(@AuthenticationPrincipal AuthUser authUser,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(paymentService.getMyPayments(authUser.memberId(), page, size));
    }
}
