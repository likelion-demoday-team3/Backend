package com.example.kiwi_backend.errorreport.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.errorreport.application.AdminErrorReportService;
import com.example.kiwi_backend.errorreport.domain.ErrorReportKind;
import com.example.kiwi_backend.errorreport.domain.ErrorReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 운영자용 오류 제보 API. SecurityConfig에서 /admin/** 는 ADMIN만 접근 가능 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminErrorReportController {

    private final AdminErrorReportService adminErrorReportService;

    /** 오류 제보 목록 (최근 발생순, 최대 200개). status·kind를 주지 않으면 전체 */
    @GetMapping("/error-reports")
    public ApiResponse<List<AdminErrorReportService.ErrorReportView>> list(
            @RequestParam(required = false) ErrorReportStatus status,
            @RequestParam(required = false) ErrorReportKind kind) {
        return ApiResponse.ok(adminErrorReportService.list(status, kind));
    }

    /** 해결 처리 */
    @PostMapping("/error-reports/{errorReportId}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long errorReportId, @AuthenticationPrincipal AuthUser admin) {
        adminErrorReportService.resolve(errorReportId, admin.memberId());
        return ApiResponse.ok();
    }
}
