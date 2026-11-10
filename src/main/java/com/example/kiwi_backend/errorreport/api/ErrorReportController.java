package com.example.kiwi_backend.errorreport.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.errorreport.application.ErrorReportService;
import com.example.kiwi_backend.errorreport.domain.ErrorReportKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ErrorReportController {

    private final ErrorReportService errorReportService;

    /**
     * 오류 제보. kind=USER(마이페이지에서 직접 제보), AUTO(앱이 자동 수집한 에러).
     * 긴 값은 잘라서 저장한다 (message 2000자, pageUrl·userAgent 500자, detail 8000자).
     * userAgent를 보내지 않으면 요청 헤더 값을 쓴다.
     */
    @PostMapping("/error-reports")
    public ApiResponse<ErrorReportService.Receipt> submit(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(value = "User-Agent", required = false) String userAgentHeader,
            @Valid @RequestBody ErrorReportRequest request) {
        String userAgent = request.userAgent() == null || request.userAgent().isBlank()
                ? userAgentHeader : request.userAgent();
        return ApiResponse.ok(errorReportService.submit(user.memberId(), new ErrorReportService.Submission(
                request.kind(), request.message(), request.pageUrl(), userAgent, request.detail())));
    }

    /** 로그인하지 않은 사람의 직접 제보 (가입이 안 될 때 등). 답변 받을 이메일이 필요하다 */
    @PostMapping("/error-reports/guest")
    public ApiResponse<ErrorReportService.Receipt> submitAsGuest(
            @RequestHeader(value = "User-Agent", required = false) String userAgentHeader,
            @Valid @RequestBody GuestErrorReportRequest request) {
        String userAgent = request.userAgent() == null || request.userAgent().isBlank()
                ? userAgentHeader : request.userAgent();
        return ApiResponse.ok(errorReportService.submitAsGuest(new ErrorReportService.GuestSubmission(
                request.contactEmail(), request.message(), request.pageUrl(), userAgent, request.detail())));
    }

    public record GuestErrorReportRequest(
            @NotBlank(message = "답변 받을 이메일을 적어주세요")
            @Email(message = "이메일 형식이 올바르지 않아요")
            @Size(max = 255, message = "이메일이 너무 길어요") String contactEmail,
            @NotBlank(message = "어떤 문제가 있었는지 적어주세요") String message,
            String pageUrl,
            String userAgent,
            String detail
    ) { }

    public record ErrorReportRequest(
            @NotNull ErrorReportKind kind,
            @NotBlank(message = "어떤 문제가 있었는지 적어주세요") String message,
            String pageUrl,
            String userAgent,
            String detail
    ) { }
}
