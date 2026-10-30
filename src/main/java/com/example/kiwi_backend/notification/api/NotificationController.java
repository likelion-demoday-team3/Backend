package com.example.kiwi_backend.notification.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 내 알림함. 최신순으로 보여주고 읽음 처리를 한다 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    public ApiResponse<NotificationService.History> list(@AuthenticationPrincipal AuthUser user,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.list(user.memberId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationService.UnreadCount> unreadCount(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(notifications.unreadCount(user.memberId()));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationService.NotificationView> markRead(@AuthenticationPrincipal AuthUser user,
                                                                     @PathVariable Long notificationId) {
        return ApiResponse.ok(notifications.markRead(user.memberId(), notificationId));
    }

    @PostMapping("/read-all")
    public ApiResponse<NotificationService.UnreadCount> markAllRead(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(notifications.markAllRead(user.memberId()));
    }
}
