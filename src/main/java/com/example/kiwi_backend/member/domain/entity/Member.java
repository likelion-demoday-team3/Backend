package com.example.kiwi_backend.member.domain.entity;

import com.example.kiwi_backend.common.crypto.EncryptedStringConverter;
import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import com.example.kiwi_backend.contract.dto.ContactType;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.domain.enumerated.MemberStatus;
import com.example.kiwi_backend.member.domain.enumerated.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name="member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    static final int MIN_AGE = 19; //만 19세, 한국나이 생일이 지난 20세
    static final int MAX_AGE = 27; //만 27세, 한국나이 생일이 지난 28세

    private static final Pattern PHONE_PATTERN = Pattern.compile("^010\\d{8}$");
    // 영문·숫자·마침표·밑줄 1~30자, 마침표로 시작·끝나거나 연속될 수 없음
    private static final Pattern INSTAGRAM_PATTERN =
            Pattern.compile("^(?!\\.)(?!.*\\.\\.)[a-z0-9._]{1,30}(?<!\\.)$");

    /** 내부 식별자, 조인용 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API·URL 노출용 식별자 */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    /** 로그인 ID, 재학생 인증 */
    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    /** 회원 이름, 가입 시 입력. 기존 회원은 없을 수 있다 */
    @Column(length = 100)
    private String name;

    /** 로그인 비밀번호 BCrypt */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** 재학 학교, 이메일 도메인에서 추출 */
    @Column(nullable = false, updatable = false)
    private String university;

    /** 성별 구분 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Gender gender;

    /** 나이 제한 검증 */
    @Column(name = "birth_date", nullable = false, updatable = false)
    private LocalDate birthDate;

    /** 공유용 연락처, 암호화. 하이픈 없이 저장 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number")
    private String phoneNumber;

    /** 공유용 연락처, 암호화. @ 없이 소문자로 저장 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "instagram_id")
    private String instagramId;

    /** 운영자 권한 구분 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    /** 정지·탈퇴 처리 */
    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false, length = 20)
    private MemberStatus status;

    /** 학교 이메일 인증 시각 (인증 후 가입하므로 항상 존재) */
    @Column(name = "email_verified_at", nullable = false, updatable = false)
    private Instant emailVerifiedAt;

    /** 본인인증 완료 시각, 추후 사용 */
    @Column(name = "identity_verified_at")
    private Instant identityVerifiedAt;

    @Column(name = "portone_identity_verification_id", unique = true, length = 40)
    private String portoneIdentityVerificationId;

    @Column(name = "pending_portone_identity_verification_id", unique = true, length = 40)
    private String pendingPortoneIdentityVerificationId;

    // ===== 생성 =====

    /**
     * 이메일 인증을 마친 사람을 회원으로 등록한다.
     * today는 테스트에서 날짜를 고정할 수 있도록 밖에서 받는다.
     */
    public static Member register(String email,
                                  String name,
                                  String passwordHash,
                                  String university,
                                  Gender gender,
                                  LocalDate birthDate,
                                  String phoneNumber,
                                  String instagramId,
                                  Instant emailVerifiedAt,
                                  LocalDate today) {
        validateAge(birthDate, today);

        Member member = new Member();
        member.publicId = UUID.randomUUID();
        member.email = requireText(email, "이메일");
        member.name = requireText(name, "이름");
        member.passwordHash = requireText(passwordHash, "비밀번호");
        member.university = requireText(university, "학교");
        member.gender = requireNonNull(gender, "성별");
        member.birthDate = birthDate;
        member.role = Role.MEMBER;
        member.status = MemberStatus.ACTIVE;
        member.emailVerifiedAt = requireNonNull(emailVerifiedAt, "이메일 인증 시각");
        member.phoneNumber = validatePhone(phoneNumber);
        member.instagramId = validateInstagram(instagramId);
        ensureAtLeastOneContact(member.phoneNumber, member.instagramId);
        return member;
    }

    /** 이름을 교체. 비워둘 수 없다. */
    public void changeName(String name) {
        ensureNotWithdrawn();
        this.name = requireText(name, "이름");
    }

    // ===== 연락처 =====

    /** 전화번호를 교체. 전화번호나 인스타 아이디 둘 중 하나는 존재해야 함. */
    public void changePhoneNumber(String phoneNumber) {
        ensureNotWithdrawn();
        String phone = validatePhone(phoneNumber);
        ensureAtLeastOneContact(phone, this.instagramId);
        this.phoneNumber = phone;
    }

    /** 인스타 아이디를 교체. 전화번호나 인스타 아이디 둘 중 하나는 존재해야 함. */
    public void changeInstagramId(String instagramId) {
        ensureNotWithdrawn();
        String instagram = validateInstagram(instagramId);
        ensureAtLeastOneContact(this.phoneNumber, instagram);
        this.instagramId = instagram;
    }

    public boolean hasContact(ContactType type) {
        return switch (type) {
            case PHONE -> phoneNumber != null;
            case INSTAGRAM -> instagramId != null;
        };
    }

    /** 공유할 연락처 값을 꺼낸다. 등록하지 않은 종류면 예외 */
    public String getContact(ContactType type) {
        if (!hasContact(type)) {
            throw new IllegalStateException(type + " 연락처가 등록되어 있지 않습니다");
        }
        return type == ContactType.PHONE ? phoneNumber : instagramId;
    }

    // ===== 계정 상태 =====

    public void changePassword(String newPasswordHash) {
        ensureNotWithdrawn();
        this.passwordHash = requireText(newPasswordHash, "비밀번호");
    }

    public void beginIdentityVerification(String verificationId) {
        ensureNotWithdrawn();
        if (identityVerifiedAt != null) {
            throw new IllegalStateException("이미 본인인증이 완료된 회원입니다");
        }
        this.pendingPortoneIdentityVerificationId = requireText(verificationId, "본인인증 ID");
    }

    public void verifyIdentity(Instant verifiedAt, String verificationId) {
        ensureNotWithdrawn();
        if (identityVerifiedAt != null) {
            if (verificationId.equals(portoneIdentityVerificationId)) {
                return;
            }
            throw new IllegalStateException("이미 본인인증이 완료된 회원입니다");
        }
        if (!verificationId.equals(pendingPortoneIdentityVerificationId)) {
            throw new IllegalArgumentException("이 계정에서 시작한 본인인증이 아닙니다");
        }
        this.identityVerifiedAt = requireNonNull(verifiedAt, "본인인증 시각");
        this.portoneIdentityVerificationId = requireText(verificationId, "본인인증 ID");
        this.pendingPortoneIdentityVerificationId = null;
    }

    public void suspend() {
        if (status != MemberStatus.ACTIVE) {
            throw new IllegalStateException("활성 회원만 정지할 수 있습니다");
        }
        this.status = MemberStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != MemberStatus.SUSPENDED) {
            throw new IllegalStateException("정지된 회원만 복구할 수 있습니다");
        }
        this.status = MemberStatus.ACTIVE;
    }

    /** 탈퇴 시 연락처는 즉시 지운다 */
    public void withdraw() {
        ensureNotWithdrawn();
        this.status = MemberStatus.WITHDRAWN;
        this.phoneNumber = null;
        this.instagramId = null;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isIdentityVerified() {
        return identityVerifiedAt != null;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** 세션 신청 등 활성 회원만 가능한 행동 앞에서 호출 */
    public void ensureActive() {
        if (!isActive()) {
            throw new IllegalStateException("이용할 수 없는 계정입니다");
        }
    }

    // ===== 내부 검증 =====

    /** 탈퇴여부 검증 */
    private void ensureNotWithdrawn() {
        if (status == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원입니다");
        }
    }

    /** 만 나이 기준 */
    private static void validateAge(LocalDate birthDate, LocalDate today) {
        requireNonNull(birthDate, "생년월일");
        int age = Period.between(birthDate, today).getYears();
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new IllegalArgumentException(
                    "만 " + MIN_AGE + "세부터 " + MAX_AGE + "세까지 가입할 수 있습니다");
        }
    }

    /** 전화번호 형식 검증 */
    private static String validatePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phone = raw.replaceAll("[\\s-]", "");
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다");
        }
        return phone;
    }

    /** 인스타그램 아이디 형식 검증 */
    private static String validateInstagram(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String instagram = raw.strip();
        if (instagram.startsWith("@")) {
            instagram = instagram.substring(1);
        }
        instagram = instagram.toLowerCase();
        if (!INSTAGRAM_PATTERN.matcher(instagram).matches()) {
            throw new IllegalArgumentException("인스타그램 아이디 형식이 올바르지 않습니다");
        }
        return instagram;
    }

    /** 전화번호나 인스타그램 아이디 중 하나는 존재해야 함 */
    private static void ensureAtLeastOneContact(String phone, String instagram) {
        if (phone == null && instagram == null) {
            throw new IllegalArgumentException("전화번호와 인스타그램 아이디 중 하나는 필요합니다");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 필수입니다");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "은(는) 필수입니다");
        }
        return value;
    }
}
