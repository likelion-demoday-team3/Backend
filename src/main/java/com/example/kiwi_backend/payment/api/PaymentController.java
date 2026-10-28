package com.example.kiwi_backend.payment.api;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.payment.api.dto.*;
import com.example.kiwi_backend.payment.application.PaymentService;
import com.example.kiwi_backend.payment.infra.nicepay.NicePayProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 보석 구매 결제 (나이스페이).
 *
 * 1. POST /payments/nicepay/ready   → 받은 값으로 프론트가 AUTHNICE.requestPay 결제창을 연다
 * 2. 결제창 인증이 끝나면 나이스페이가 브라우저로 POST /payments/nicepay/return (로그인 토큰 없음)
 *    → 승인·보석 지급 후 프론트 /shop/payment/{orderId}/success 또는 /fail 로 보낸다
 *    (결제창을 닫거나 열지 못하면 프론트가 POST /payments/{orderId}/abort)
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String APPROVE_FAILED = "APPROVE_FAILED";
    private static final String UNKNOWN_ORDER = "unknown";
    /** 우리가 만드는 주문 번호(UUID) 모양. redirect 경로에 들어가므로 다른 문자는 받지 않는다 */
    private static final Pattern ORDER_ID = Pattern.compile("[0-9a-fA-F-]{1,64}");

    private final PaymentService paymentService;
    private final NicePayProperties nicePayProperties;

    /** 결제 준비 */
    @PostMapping("/nicepay/ready")
    public ApiResponse<PaymentReadyResponse> ready(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody PaymentReadyRequest request) {
        return ApiResponse.ok(paymentService.ready(authUser.memberId(), request.productId()));
    }

    /**
     * 나이스페이 결제창 returnUrl. 나이스페이가 브라우저를 통해 form으로 보내므로 JSON 대신 redirect로 응답한다.
     * SecurityConfig에서 인증 없이 열어 두고, 위변조는 PaymentService가 나이스페이 서명으로 확인한다.
     */
    @PostMapping(value = "/nicepay/return", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> nicePayReturn(@ModelAttribute NicePayReturnRequest request) {
        String orderId = request.orderId() != null && ORDER_ID.matcher(request.orderId()).matches()
                ? request.orderId() : UNKNOWN_ORDER;
        if (UNKNOWN_ORDER.equals(orderId)) {
            return redirectToFailure(orderId, APPROVE_FAILED, "결제 정보를 확인할 수 없어요.");
        }

        if (!request.authenticated()) {
            try {
                paymentService.failAuthentication(orderId, request.authResultMsg());
            } catch (RuntimeException e) {
                log.warn("나이스페이 인증 실패 저장 실패 orderId={}", orderId, e);
            }
            return redirectToFailure(orderId, request.authResultCode(), request.authResultMsg());
        }

        try {
            PaymentApproveResponse result = paymentService.approve(request);
            URI location = UriComponentsBuilder.fromUriString(nicePayProperties.resultUrlFor(result.orderId()) + "/success")
                    .queryParam("grantedGem", result.grantedGem())
                    .queryParam("gemBalance", result.gemBalance())
                    .encode().build().toUri();
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
        } catch (IllegalStateException | NotFoundException | PaymentGatewayException e) {
            return redirectToFailure(orderId, APPROVE_FAILED, e.getMessage());
        } catch (RuntimeException e) {
            log.error("나이스페이 승인 처리 중 예상하지 못한 오류 orderId={}", orderId, e);
            return redirectToFailure(orderId, APPROVE_FAILED, "결제 승인에 실패했어요. 다시 시도해주세요.");
        }
    }

    /** 결제창을 닫거나 열지 못함 */
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

    private ResponseEntity<Void> redirectToFailure(String orderId, String code, String message) {
        URI location = UriComponentsBuilder.fromUriString(nicePayProperties.resultUrlFor(orderId) + "/fail")
                .queryParam("code", code == null ? "" : code)
                .queryParam("message", message == null ? "" : message)
                .encode().build().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
