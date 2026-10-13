package com.example.kiwi_backend.realtime.presentation;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.realtime.dto.IceServerResponse;
import com.example.kiwi_backend.realtime.service.IceServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/live/sessions/{sessionId}/ice-servers")
public class IceServerController {

    private final IceServerService iceServerService;

    @GetMapping
    public ApiResponse<IceServerResponse> getIceServers(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(iceServerService.getForMember(sessionId, authUser.memberId()));
    }
}
