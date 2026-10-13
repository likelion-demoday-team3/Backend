package com.example.kiwi_backend.interaction.application;
import com.example.kiwi_backend.contract.command.SessionLiveAccess;
import com.example.kiwi_backend.contract.command.ResultWindowAccess;
import com.example.kiwi_backend.contract.dto.LiveSessionContext;
import com.example.kiwi_backend.interaction.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Service
@RequiredArgsConstructor
public class PostSessionWindowService implements ResultWindowAccess {
    private final SessionLiveAccess access;
    private final PostSessionWindowRepository windows;
    private final Clock clock;

    @Transactional
    public WindowState state(Long sessionId, Long memberId) {
        requireResults(sessionId, memberId);
        var window = windows.findBySessionIdAndMemberId(sessionId, memberId).orElse(null);
        return new WindowState(window != null && window.getResultsClosedAt() != null,
                window != null && window.getContactsClosedAt() != null);
    }

    @Transactional
    public void closeResults(Long sessionId, Long memberId) {
        requireResults(sessionId, memberId);
        window(sessionId, memberId).closeResults(clock.instant());
    }

    @Transactional
    public void closeContacts(Long sessionId, Long memberId) {
        requireResults(sessionId, memberId);
        window(sessionId, memberId).closeContacts(clock.instant());
    }

    @Transactional
    public void requireResultOpen(Long sessionId, Long memberId) {
        if (state(sessionId, memberId).resultsClosed()) throw new IllegalStateException("Result window is closed");
    }

    @Transactional
    public void requireContactOpen(Long sessionId, Long memberId) {
        var state = state(sessionId, memberId);
        if (!state.resultsClosed() || state.contactsClosed()) throw new IllegalStateException("Contact window is not open");
    }

    private void requireResults(Long sessionId, Long memberId) {
        var context = access.lock(sessionId);
        context.requireParticipant(memberId);
        if (context.phase() != LiveSessionContext.Phase.RESULT_AND_SHARE) throw new IllegalStateException("Results not ready");
    }

    private PostSessionWindow window(Long sessionId, Long memberId) {
        return windows.findBySessionIdAndMemberId(sessionId, memberId)
                .orElseGet(() -> windows.save(PostSessionWindow.create(sessionId, memberId)));
    }
    public record WindowState(boolean resultsClosed, boolean contactsClosed) { }
}
