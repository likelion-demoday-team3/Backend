package com.example.kiwi_backend.interaction.domain;
import com.example.kiwi_backend.contract.dto.ContactType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "contact_request", uniqueConstraints = @UniqueConstraint(name = "uk_contact_request_pair",
        columnNames = {"session_id", "requester_id", "target_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactRequest {
    public enum Status { PENDING, ACCEPTED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long requesterId;
    @Column(nullable = false, updatable = false) private Long targetId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContactType contactType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int chargedGem;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant respondedAt;
    public static ContactRequest create(Long sessionId, Long requester, Long target, ContactType type, Instant now) {
        var request = new ContactRequest();
        request.sessionId = sessionId; request.requesterId = requester; request.targetId = target;
        request.contactType = type; request.status = Status.PENDING; request.createdAt = now;
        return request;
    }
    public void respond(boolean accepted, int cost, Instant now) {
        if (status != Status.PENDING) throw new IllegalStateException("Request already answered");
        status = accepted ? Status.ACCEPTED : Status.REJECTED;
        chargedGem = cost; respondedAt = now;
    }
}
