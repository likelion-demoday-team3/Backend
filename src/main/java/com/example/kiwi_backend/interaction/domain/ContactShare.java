package com.example.kiwi_backend.interaction.domain;
import com.example.kiwi_backend.contract.dto.ContactType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "contact_share", uniqueConstraints = @UniqueConstraint(name = "uk_contact_share",
        columnNames = {"session_id", "sender_id", "receiver_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactShare {
    public enum ShareType { AUTO_MATCH, MANUAL, REQUEST_ACCEPTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long senderId;
    @Column(nullable = false, updatable = false) private Long receiverId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContactType contactType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ShareType shareType;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    public static ContactShare create(Long sessionId, Long sender, Long receiver, ContactType type, ShareType shareType, Instant now) {
        var share = new ContactShare();
        share.sessionId = sessionId; share.senderId = sender; share.receiverId = receiver;
        share.contactType = type; share.shareType = shareType; share.createdAt = now;
        return share;
    }
}
