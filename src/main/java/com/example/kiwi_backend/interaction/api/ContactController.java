package com.example.kiwi_backend.interaction.api;
import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.contract.dto.ContactType;
import com.example.kiwi_backend.interaction.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ContactController {
    private final ContactService contacts;
    private final PostSessionWindowService windows;
    private final LiveSessionStateService liveState;

    @GetMapping("/sessions/{sessionId}/live/state")
    public ApiResponse<LiveSessionStateService.State> liveState(@PathVariable Long sessionId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(liveState.get(sessionId, user.memberId()));
    }

    @PostMapping("/sessions/{sessionId}/contacts/send")
    public ApiResponse<ContactService.ShareView> send(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody Target request) {
        return ApiResponse.ok(contacts.send(sessionId, user.memberId(), request.targetId(), request.contactType()));
    }
    @PostMapping("/sessions/{sessionId}/contacts/requests")
    public ApiResponse<ContactService.RequestView> request(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody Target request) {
        return ApiResponse.ok(contacts.request(sessionId, user.memberId(), request.targetId(), request.contactType()));
    }
    @PostMapping("/sessions/{sessionId}/contacts/requests/{requestId}/response")
    public ApiResponse<ContactService.RequestView> respond(@PathVariable Long sessionId, @PathVariable Long requestId,
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody Decision decision) {
        return ApiResponse.ok(contacts.respond(sessionId, requestId, user.memberId(), decision.accept()));
    }
    @GetMapping("/contact-history/shares")
    public ApiResponse<ContactService.History<ContactService.ShareView>> shares(@AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(contacts.shareHistory(user.memberId(), page, size));
    }
    @GetMapping("/contact-history/requests")
    public ApiResponse<ContactService.History<ContactService.RequestView>> requests(@AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(contacts.requestHistory(user.memberId(), page, size));
    }
    @GetMapping("/sessions/{sessionId}/windows")
    public ApiResponse<PostSessionWindowService.WindowState> state(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(windows.state(sessionId, user.memberId()));
    }
    @PostMapping("/sessions/{sessionId}/results/close")
    public ApiResponse<Void> closeResults(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        windows.closeResults(sessionId, user.memberId()); return ApiResponse.ok();
    }
    @PostMapping("/sessions/{sessionId}/contacts/close")
    public ApiResponse<Void> closeContacts(@PathVariable Long sessionId, @AuthenticationPrincipal AuthUser user) {
        windows.closeContacts(sessionId, user.memberId()); return ApiResponse.ok();
    }
    public record Target(@NotNull Long targetId, @NotNull ContactType contactType) { }
    public record Decision(@NotNull Boolean accept) { }
}
