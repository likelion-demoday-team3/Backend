package com.example.kiwi_backend.auth.application;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 가입 가능한 학교와 이메일 도메인, 학교 지역.
 * 목록은 src/main/resources/university-domains.csv ("도메인,학교 이름,지역")에 있다. 학교를 추가할 때는 그 파일만 고친다.
 * 정확히 같은 도메인이나 그 하위 도메인(예: g.skku.edu)이면 그 학교로 본다.
 */
@Component
public class UniversityDomainPolicy {

    static final String RESOURCE = "university-domains.csv";
    static final Set<String> REGIONS = Set.of("서울", "경기", "인천", "강원", "대전", "세종", "충북", "충남",
            "광주", "전북", "전남", "대구", "경북", "부산", "울산", "경남", "제주");

    /** 학교 이름과 지역(시·도) */
    public record School(String name, String region) { }

    private final Map<String, School> domainToSchool;

    public UniversityDomainPolicy() {
        this.domainToSchool = load(RESOURCE);
    }

    /** 허용된 학교 이메일이면 학교 이름을, 아니면 예외 */
    public String universityOf(String email) {
        return schoolOf(email).name();
    }

    /** 허용된 학교 이메일이면 학교(이름·지역)를, 아니면 예외 */
    public School schoolOf(String email) {
        String domain = domainOf(email);
        // 하위 도메인부터 한 단계씩 줄여가며 찾는다: mail.hongik.ac.kr → hongik.ac.kr → ac.kr → kr
        String candidate = domain;
        while (true) {
            School school = domainToSchool.get(candidate);
            if (school != null) {
                return school;
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) {
                break;
            }
            candidate = candidate.substring(dot + 1);
        }
        throw new IllegalArgumentException("가입할 수 있는 학교 이메일이 아닙니다");
    }

    /** 등록된 도메인 → 학교 (읽기 전용) */
    public Map<String, School> domains() {
        return domainToSchool;
    }

    private static String domainOf(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }
        return email.substring(at + 1).trim().toLowerCase();
    }

    static Map<String, School> load(String resource) {
        Map<String, School> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",", -1);
                if (parts.length != 3) {
                    throw new IllegalStateException(resource + " " + lineNumber + "번째 줄 형식이 잘못됐습니다: " + line);
                }
                String domain = parts[0].strip().toLowerCase();
                String name = parts[1].strip();
                String region = parts[2].strip();
                if (domain.isEmpty() || !domain.contains(".") || name.isEmpty() || !REGIONS.contains(region)) {
                    throw new IllegalStateException(resource + " " + lineNumber + "번째 줄 형식이 잘못됐습니다: " + line);
                }
                if (result.putIfAbsent(domain, new School(name, region)) != null) {
                    throw new IllegalStateException(resource + "에 같은 도메인이 두 번 있습니다: " + domain);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(resource + "를 읽지 못했습니다", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(resource + "에 학교가 하나도 없습니다");
        }
        return Collections.unmodifiableMap(result);
    }
}
