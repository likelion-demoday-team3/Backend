package com.example.kiwi_backend.auth.application;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 가입 가능한 학교와 이메일 도메인.
 *
 * TODO 각 학교의 "학생용" 메일 도메인을 실제로 확인해서 채울 것.
 *  학교에 따라 교직원용과 학생용 도메인이 다르거나, 학생 메일이 별도 도메인을 쓰는 경우가 있다.
 *  졸업 후에도 메일을 계속 쓸 수 있는 학교가 있는지도 확인 (나이 제한이 대부분 걸러주긴 함).
 */
@Component
public class UniversityDomainPolicy {

    private static final Map<String, String> DOMAIN_TO_UNIVERSITY = Map.of(
            "yonsei.ac.kr", "연세대학교",
            "sogang.ac.kr", "서강대학교",
            "ewha.ac.kr", "이화여자대학교",
            "hongik.ac.kr", "홍익대학교"
    );

    /** 허용된 학교 이메일이면 학교 이름을, 아니면 예외 */
    public String universityOf(String email) {
        String domain = domainOf(email);
        for (Map.Entry<String, String> entry : DOMAIN_TO_UNIVERSITY.entrySet()) {
            String allowed = entry.getKey();
            // 정확히 같은 도메인 또는 그 하위 도메인 (예: mail.hongik.ac.kr)
            if (domain.equals(allowed) || domain.endsWith("." + allowed)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("가입할 수 있는 학교 이메일이 아닙니다");
    }

    private static String domainOf(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }
        return email.substring(at + 1).toLowerCase();
    }
}
