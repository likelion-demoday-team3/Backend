package com.example.kiwi_backend.live.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.live.api.dto.LiveSessionStartResponse;
import com.example.kiwi_backend.live.application.LiveSessionStartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live/sessions/{sessionId}")
@RequiredArgsConstructor
public class LiveSessionStartController {

    private final LiveSessionStartService liveSessionStartService;

    // TODO for 10/12 update: 반환값 처리
    @PostMapping("/start")
    public ApiResponse<LiveSessionStartResponse> startLiveSession(@PathVariable Long sessionId) {
        return ApiResponse.ok(LiveSessionStartResponse.from(
                liveSessionStartService.startLiveSession(sessionId)
        ));
    }
}
