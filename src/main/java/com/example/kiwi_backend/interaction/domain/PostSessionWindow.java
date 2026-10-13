package com.example.kiwi_backend.interaction.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "post_session_window", uniqueConstraints = @UniqueConstraint(name = "uk_post_session_window",
        columnNames = {"session_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostSessionWindow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long memberId;
    private Instant resultsClosedAt;
    private Instant contactsClosedAt;
    public static PostSessionWindow create(Long sessionId, Long memberId) {
        var window = new PostSessionWindow();
        window.sessionId = sessionId; window.memberId = memberId;
        return window;
    }
    public void closeResults(Instant now) { if (resultsClosedAt == null) resultsClosedAt = now; }
    public void closeContacts(Instant now) {
        if (resultsClosedAt == null) throw new IllegalStateException("Close results first");
        if (contactsClosedAt == null) contactsClosedAt = now;
    }
}
