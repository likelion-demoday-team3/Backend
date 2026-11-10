package com.example.kiwi_backend.auth.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 전국 4년제 대학 학교 메일로 가입할 수 있다 */
class UniversityDomainPolicyTest {

    private final UniversityDomainPolicy policy = new UniversityDomainPolicy();

    @Test
    void existingSchoolsStillWork() {
        assertThat(policy.universityOf("a@yonsei.ac.kr")).isEqualTo("연세대학교");
        assertThat(policy.universityOf("a@sogang.ac.kr")).isEqualTo("서강대학교");
        assertThat(policy.universityOf("a@ewha.ac.kr")).isEqualTo("이화여자대학교");
        assertThat(policy.universityOf("a@hongik.ac.kr")).isEqualTo("홍익대학교");
    }

    @Test
    void nationwideSchoolsAndSubdomains() {
        assertThat(policy.universityOf("a@snu.ac.kr")).isEqualTo("서울대학교");
        assertThat(policy.universityOf("a@pusan.ac.kr")).isEqualTo("부산대학교");
        assertThat(policy.universityOf("a@g.skku.edu")).isEqualTo("성균관대학교");
        assertThat(policy.universityOf("a@ewhain.net")).isEqualTo("이화여자대학교");
        assertThat(policy.universityOf("A@Mail.Hongik.AC.KR")).isEqualTo("홍익대학교");
        assertThat(policy.universityOf("a@jejunu.ac.kr")).isEqualTo("제주대학교");
    }

    @Test
    void similarLookingDomainsAreNotConfused() {
        // cu.ac.kr(대구가톨릭대)이 kcu.ac.kr 같은 다른 도메인에 걸리면 안 된다
        assertThatThrownBy(() -> policy.universityOf("a@kcu.ac.kr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.universityOf("a@notyonsei.ac.kr")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonSchoolOrMalformedEmails() {
        assertThatThrownBy(() -> policy.universityOf("a@gmail.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.universityOf("a@ac.kr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.universityOf("no-at-sign")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.universityOf("a@")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schoolRegionComesWithTheSchool() {
        assertThat(policy.schoolOf("a@yonsei.ac.kr")).isEqualTo(new UniversityDomainPolicy.School("연세대학교", "서울"));
        assertThat(policy.schoolOf("a@pusan.ac.kr").region()).isEqualTo("부산");
        assertThat(policy.schoolOf("a@inha.edu").region()).isEqualTo("인천");
        assertThat(policy.schoolOf("a@postech.ac.kr").region()).isEqualTo("경북");
        assertThat(policy.schoolOf("a@jejunu.ac.kr").region()).isEqualTo("제주");
    }

    @Test
    void listIsWellFormed() {
        assertThat(policy.domains().values())
                .allSatisfy(school -> assertThat(UniversityDomainPolicy.REGIONS).contains(school.region()));
        // 같은 학교인데 도메인마다 지역이 다르게 적히면 안 된다
        var regionsBySchool = new java.util.HashMap<String, String>();
        policy.domains().values().forEach(school -> {
            String before = regionsBySchool.putIfAbsent(school.name(), school.region());
            assertThat(before == null || before.equals(school.region()))
                    .as("%s 지역이 도메인마다 다릅니다", school.name()).isTrue();
        });
        assertThat(policy.domains()).hasSizeGreaterThan(150);
        assertThat(policy.domains().keySet()).allSatisfy(domain -> assertThat(domain).isLowerCase().contains("."));
    }
}
