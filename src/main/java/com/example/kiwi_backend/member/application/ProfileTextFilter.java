package com.example.kiwi_backend.member.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 프로필 자유 입력란에 연락처나 외부 링크를 적는 것을 막는다.
 * 매칭을 거치지 않고 연락처가 퍼지면 서비스 구조가 무력해지기 때문.
 *
 * "카톡", "인스타" 같은 단어 자체는 막지 않는다.
 * 연락 빈도 칸에 "카톡보다 전화가 편해요"처럼 자연스럽게 쓰이기 때문에,
 * 실제 아이디·번호·링크처럼 보이는 패턴만 차단한다.
 */
@Component
public class ProfileTextFilter {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            // 전화번호: 010-1234-5678, 010 1234 5678, 01012345678 등
            Pattern.compile("01[016789][\\s.\\-]?\\d{3,4}[\\s.\\-]?\\d{4}"),
            // @아이디
            Pattern.compile("@[A-Za-z0-9._]{2,}"),
            // "아이디: abc123", "id abc123"
            Pattern.compile("(?i)(아이디|\\bid)\\s*[:：]?\\s*[A-Za-z0-9._]{3,}"),
            // 링크
            Pattern.compile("(?i)(https?://|www\\.|open\\.kakao|instagram\\.com)")
    );

    /** 연락처처럼 보이면 예외 */
    public void check(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(value).find()) {
                throw new IllegalArgumentException(
                        fieldName + "에는 연락처나 외부 링크를 적을 수 없어요. 연락처는 매칭 후에 공유돼요");
            }
        }
    }
}

