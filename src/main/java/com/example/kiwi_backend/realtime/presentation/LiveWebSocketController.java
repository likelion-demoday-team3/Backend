package com.example.kiwi_backend.realtime.presentation;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.realtime.dto.LiveJoinRequest;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
import com.example.kiwi_backend.realtime.service.LiveJoinService;
import com.example.kiwi_backend.realtime.service.RealtimeSignalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LiveWebSocketController {
//    HTTP 요청이 아니라 WebSocket 메시지를 받는 컨트롤러

    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeSignalService realtimeSignalService;
    private final LiveJoinService liveJoinService;

    @MessageMapping("/live/join")
    public void join(@Valid LiveJoinRequest request, @AuthenticationPrincipal AuthUser authUser) {
        Long memberId = requireMemberId(authUser);
//        보낸 사람을 로그인 정보에서 가져온다. (프론트가 정보를 보내지 X)

        messagingTemplate.convertAndSendToUser(
                memberId.toString(),
                "/queue/live/joined",
                liveJoinService.join(request.sessionId(), memberId)
        );
//        입장 확인 메시지를 보낸다
    }

    @MessageMapping("/live/signal")
    public void signal(@Valid SignalRequest request, @AuthenticationPrincipal AuthUser authUser) {
        Long fromMemberId = requireMemberId(authUser);
        realtimeSignalService.sendSignal(request, fromMemberId);
    }

    private Long requireMemberId(AuthUser authUser) {
//        authUser가 진짜 있는지 확인하고, memberId를 꺼낸다
        if (authUser == null || authUser.memberId() == null) {
            throw new IllegalArgumentException("authenticated member is required");
        }
        return authUser.memberId();
//        로그인 정보가 없으면 에러고 있으면 memberId 반환
    }
}
