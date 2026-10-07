package com.example.kiwi_backend.member.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** 프로필 카드의 관심사 태그. Profile을 통해서만 생성·삭제한다 */
@Entity
@Table(
        name = "profile_interest",
        uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "interest"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileInterest {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 프로필 연결 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private Profile profile;

    /** 카드에 보여줄 관심사 태그 */
    @Column(nullable = false, length = 40, updatable = false)
    private String interest;

    /** 등록 시각 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    ProfileInterest(Profile profile, String interest) {
        this.profile = profile;
        this.interest = interest;
    }
}
