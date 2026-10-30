package com.example.kiwi_backend.member.domain.entity;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 세션 중 대화 상대에게 보여주는 프로필 카드.
 * 연락처는 여기에 두지 않는다 (카드 응답에 섞여 노출되는 것을 막기 위함).
 */
@Entity
@Table(name = "profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseTimeEntity {

    static final int MAX_TEXT_LENGTH = 200;
    static final int MAX_INTERESTS = 5;
    static final int MAX_INTEREST_LENGTH = 20;

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 회원 연결 1:1 (같은 모듈이라 연관관계 허용) */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true, updatable = false)
    private Member member;

    /** 카드 표시용 학교, 생성 시 MEMBER에서 복사하고 이후 변경 불가 */
    @Column(nullable = false, updatable = false)
    private String university;

    /** 카드·궁합용 이상형 */
    @Column(columnDefinition = "text")
    private String idealType;

    /** 카드·궁합용 데이트 스타일 */
    @Column(columnDefinition = "text")
    private String datingStyle;

    /** 카드·궁합용 연락 빈도 */
    @Column(columnDefinition = "text")
    private String contactStyle;

    /** 카드·궁합용 연애 성향 */
    @Column(columnDefinition = "text")
    private String loveStyle;

    /** 관심사 태그. 외부에서 리스트를 직접 수정하지 못하게 getter를 막는다 */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileInterest> interests = new ArrayList<>();

    // ===== 생성 =====

    public static Profile create(Member member) {
        Profile profile = new Profile();
        profile.member = Objects.requireNonNull(member);
        profile.university = member.getUniversity();
        return profile;
    }

    // ===== 수정 =====

    /**
     * 자유 입력 항목을 수정한다. 빈 값은 null로 저장.
     * 연락처 패턴(전화번호, @아이디) 차단은 정책이 바뀔 수 있어 application 계층에서 한다.
     */
    public void update(String idealType, String datingStyle, String contactStyle, String loveStyle) {
        this.idealType = clean(idealType, "이상형");
        this.datingStyle = clean(datingStyle, "데이트 스타일");
        this.contactStyle = clean(contactStyle, "연락 빈도");
        this.loveStyle = clean(loveStyle, "연애 성향");
    }

    /**
     * 세션 신청에 필요한 만큼 카드를 채웠는가: 문장 항목 4개를 모두 쓰고 관심사를 하나 이상 넣었다.
     * 빈 문장은 update에서 null로 저장되므로 null만 확인하면 된다.
     */
    public boolean isComplete() {
        return idealType != null && datingStyle != null && contactStyle != null && loveStyle != null
                && !interests.isEmpty();
    }

    /**
     * 관심사를 요청한 목록으로 맞춘다.
     * 전부 지우고 새로 넣으면 Hibernate가 insert를 delete보다 먼저 실행해서
     * (profile_id, interest) 유니크에 걸린다. 그래서 바뀐 것만 지우고 더한다.
     */
    public void replaceInterests(Collection<String> requested) {
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String raw : requested == null ? List.<String>of() : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String value = raw.strip();
            if (value.length() > MAX_INTEREST_LENGTH) {
                throw new IllegalArgumentException("관심사는 " + MAX_INTEREST_LENGTH + "자 이하로 입력해주세요");
            }
            next.add(value);
        }
        if (next.size() > MAX_INTERESTS) {
            throw new IllegalArgumentException("관심사는 최대 " + MAX_INTERESTS + "개까지 선택할 수 있습니다");
        }

        interests.removeIf(existing -> !next.contains(existing.getInterest()));

        Set<String> kept = interests.stream()
                .map(ProfileInterest::getInterest)
                .collect(Collectors.toSet());
        for (String value : next) {
            if (!kept.contains(value)) {
                interests.add(new ProfileInterest(this, value));
            }
        }
    }

    /** 카드에 보여줄 관심사 이름 목록 */
    public List<String> getInterestNames() {
        return interests.stream().map(ProfileInterest::getInterest).toList();
    }

    private static String clean(String value, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + "은(는) " + MAX_TEXT_LENGTH + "자 이하로 입력해주세요");
        }
        return trimmed;
    }
}
