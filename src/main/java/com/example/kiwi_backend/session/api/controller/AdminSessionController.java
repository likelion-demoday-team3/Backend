package com.example.kiwi_backend.session.api.controller;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.session.api.dto.SessionCreateRequest;
import com.example.kiwi_backend.session.application.service.AdminSessionService;
import com.example.kiwi_backend.session.application.service.SessionDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 운영자용 세션 API. SecurityConfig에서 /admin/** 는 ADMIN만 접근 가능 */
@RestController
@RequestMapping("/admin/sessions")
@RequiredArgsConstructor
public class AdminSessionController {

    private final AdminSessionService adminSessionService;
    private final SessionDecisionService sessionDecisionService;

    /** 세션 생성 (SCHEDULED 상태로 만들어짐) */
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody SessionCreateRequest request) {
        return ApiResponse.ok(adminSessionService.create(request));
    }

    /** 설정 수정. 모집 시작 전에만 가능 */
    @PutMapping("/{sessionId}")
    public ApiResponse<Void> update(@PathVariable Long sessionId, @Valid @RequestBody SessionCreateRequest request) {
        adminSessionService.update(sessionId, request);
        return ApiResponse.ok();
    }

    /** 모집 시작 */
    @PostMapping("/{sessionId}/open")
    public ApiResponse<Void> open(@PathVariable Long sessionId) {
        adminSessionService.openRecruiting(sessionId);
        return ApiResponse.ok();
    }

    /** 운영자 취소. 신청자 전원 100% 환불 */
    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long sessionId) {
        sessionDecisionService.cancelByAdmin(sessionId);
        return ApiResponse.ok();
    }
}
