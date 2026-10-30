package com.example.kiwi_backend.session.api.controller;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.session.api.dto.CallTestSessionRequest;
import com.example.kiwi_backend.session.api.dto.CallTestSessionResponse;
import com.example.kiwi_backend.session.application.service.AdminCallTestSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sessions")
@ConditionalOnProperty(name = "app.session.call-test.enabled", havingValue = "true")
public class AdminCallTestSessionController {

    private final AdminCallTestSessionService testSessionService;

    @PostMapping("/test-call")
    public ApiResponse<CallTestSessionResponse> create(@Valid @RequestBody CallTestSessionRequest request) {
        return ApiResponse.ok(testSessionService.create(request));
    }
}
