package com.example.kiwi_backend.realtime.presentation;

import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.realtime.dto.LiveJoinRequest;
import com.example.kiwi_backend.realtime.dto.LiveJoinResponse;
import com.example.kiwi_backend.realtime.dto.SignalMessage;
import com.example.kiwi_backend.realtime.dto.SignalRequest;
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

    @MessageMapping("/live/join")
    public void join(@Valid LiveJoinRequest request, @AuthenticationPrincipal AuthUser authUser) {
        Long memberId = requireMemberId(authUser);

        messagingTemplate.convertAndSendToUser(
                memberId.toString(),
                "/queue/live/joined",
                new LiveJoinResponse(request.sessionId(), memberId, "JOINED")
        );
//        입장 확인 메시지
    }

    @MessageMapping("/live/signal")
    public void signal(@Valid SignalRequest request, @AuthenticationPrincipal AuthUser authUser) {
        Long fromMemberId = requireMemberId(authUser);
        if (fromMemberId.equals(request.toMemberId())) {
            throw new IllegalArgumentException("cannot send signal to self");
        }

        messagingTemplate.convertAndSendToUser(
                request.toMemberId().toString(),
                "/queue/live/signal",
                new SignalMessage(
                        request.sessionId(),
                        request.roomId(),
                        fromMemberId,
                        request.toMemberId(),
                        request.type(),
                        request.payload()
                )
        );
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
