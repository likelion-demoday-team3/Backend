package com.example.kiwi_backend.common.notification;

import com.example.kiwi_backend.contract.dto.ContactType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 알림 문구에 공통으로 쓰는 표현. 화면과 같은 한국 시간으로 보여준다 */
public final class NotificationTexts {

    private static final DateTimeFormatter SESSION_TIME =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA).withZone(ZoneId.of("Asia/Seoul"));

    private NotificationTexts() {
    }

    /** 예: 11월 1일 21:00 */
    public static String sessionTime(Instant startTime) {
        return SESSION_TIME.format(startTime);
    }

    public static String contactLabel(ContactType type) {
        return type == ContactType.PHONE ? "전화번호" : "인스타그램 아이디";
    }

    /** 상대 이름은 실명이 아니라 세션 별칭으로 부른다. 별칭을 모르면 "상대방" */
    public static String partner(String alias) {
        return alias == null || alias.isBlank() ? "상대방" : alias + "님";
    }
}
