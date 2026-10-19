package com.example.kiwi_backend.common.exception;

import com.example.kiwi_backend.auth.application.service.VerificationCodeMismatchException;
import com.example.kiwi_backend.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 엔티티·서비스가 던지는 예외를 공통 응답 형식으로 바꾼다.
 * 나중에 BusinessException + ErrorCode 체계를 만들면 여기에 추가.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_INPUT", message));
    }

    /** 엔티티의 입력값 검증 실패 (형식 오류, 필수값 누락 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_INPUT", e.getMessage()));
    }

    /** 현재 상태에서 할 수 없는 행동 (탈퇴 회원, 중복 가입 등) */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("INVALID_STATE", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailed(AuthenticationFailedException e){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("LOGIN_FAILED", e.getMessage()));
    }

    /** 토스페이먼츠 등 외부 결제사 호출 실패 */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentGateway(PaymentGatewayException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.fail("PAYMENT_FAILED", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("INVALID_INPUT", "요청 형식이 올바르지 않습니다."));
    }
}
