package com.example.kiwi_backend.interaction.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.interaction.application.HeartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions/{sessionId}/hearts")
public class HeartController {
    private final HeartService service;
    @PostMapping
    public ApiResponse<HeartService.Receipt> send(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody SendHeart request) {
        return ApiResponse.ok(service.send(sessionId, user.memberId(), request.receiverId()));
    }
    @GetMapping
    public ApiResponse<HeartService.HeartStatus> status(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.status(sessionId, user.memberId()));
    }
    public record SendHeart(@NotNull Long receiverId) { }
}
