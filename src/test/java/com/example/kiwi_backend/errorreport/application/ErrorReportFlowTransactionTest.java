package com.example.kiwi_backend.errorreport.application;

import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.common.security.JwtProvider;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.errorreport.domain.ErrorReportKind;
import com.example.kiwi_backend.errorreport.domain.ErrorReportStatus;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.enumerated.Role;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import com.example.kiwi_backend.notification.application.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 오류 제보 접수 → 운영자 알림 → 운영자 목록·해결 처리 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorReportFlowTransactionTest {

    @Autowired ErrorReportService errorReports;
    @Autowired AdminErrorReportService adminErrorReports;
    @Autowired MemberRepository members;
    @Autowired NotificationService notifications;
    @Autowired JwtProvider jwtProvider;
    @Autowired MockMvc mockMvc;

    private Long user;
    private Long admin;

    @BeforeEach
    void setup() {
        user = member(false);
        admin = member(true);
    }

    @Test
    void userReportIsSavedAndNotifiesAdmins() {
        var receipt = errorReports.submit(user, new ErrorReportService.Submission(ErrorReportKind.USER,
                "  세션 화면에서 상대 소리가 안 들려요  ", "https://kiiwee.vercel.app/session/3", "UA", null));

        assertThat(receipt.id()).isNotNull();
        assertThat(receipt.merged()).isFalse();
        var view = adminErrorReports.list(ErrorReportStatus.OPEN, ErrorReportKind.USER).stream()
                .filter(item -> item.id().equals(receipt.id())).findFirst().orElseThrow();
        assertThat(view.message()).isEqualTo("세션 화면에서 상대 소리가 안 들려요");
        assertThat(view.reporter().memberId()).isEqualTo(user);
        assertThat(view.reporter().email()).endsWith("@yonsei.ac.kr");

        var adminInbox = notifications.list(admin, 0, 20).items();
        assertThat(adminInbox).anySatisfy(item -> {
            assertThat(item.type()).isEqualTo(NotificationType.ERROR_REPORTED);
            assertThat(item.link()).isEqualTo("/admin/error-reports");
        });
    }

    @Test
    void sameAutoErrorIsMergedAndNotifiedOnce() {
        String message = "TypeError: Cannot read properties of null " + UUID.randomUUID();
        var first = errorReports.submit(user, auto(message, "https://kiiwee.vercel.app/store?x=1"));
        // 같은 에러, 같은 화면(쿼리만 다름) → 새로 만들지 않고 횟수만 올린다
        var second = errorReports.submit(user, auto(message, "https://kiiwee.vercel.app/store?x=2"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.merged()).isTrue();
        var view = adminErrorReports.list(null, ErrorReportKind.AUTO).stream()
                .filter(item -> item.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(view.occurrenceCount()).isEqualTo(2);
        long notified = notifications.list(admin, 0, 50).items().stream()
                .filter(item -> item.type() == NotificationType.ERROR_REPORTED && item.body().contains(message.substring(0, 20)))
                .count();
        assertThat(notified).isEqualTo(1);

        // 해결 처리한 뒤 같은 에러가 다시 나면 새 제보로 만든다
        adminErrorReports.resolve(first.id(), admin);
        var third = errorReports.submit(user, auto(message, "https://kiiwee.vercel.app/store"));
        assertThat(third.id()).isNotEqualTo(first.id());
    }

    @Test
    void tooManyReportsInAnHourAreLimited() {
        for (int i = 0; i < ErrorReportService.MAX_PER_HOUR; i++) {
            errorReports.submit(user, auto("에러 " + i + " " + UUID.randomUUID(), "https://kiiwee.vercel.app/"));
        }
        // 자동 수집은 조용히 버리고, 직접 제보는 다시 시도하라고 알린다
        var dropped = errorReports.submit(user, auto("넘친 에러 " + UUID.randomUUID(), null));
        assertThat(dropped.id()).isNull();
        assertThatThrownBy(() -> errorReports.submit(user, new ErrorReportService.Submission(
                ErrorReportKind.USER, "제보", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void longValuesAreClipped() {
        var receipt = errorReports.submit(user, new ErrorReportService.Submission(ErrorReportKind.USER,
                "가".repeat(3000), "https://x/" + "a".repeat(600), "U".repeat(600), "d".repeat(9000)));
        var view = adminErrorReports.list(null, null).stream()
                .filter(item -> item.id().equals(receipt.id())).findFirst().orElseThrow();
        assertThat(view.message()).hasSize(2000);
        assertThat(view.pageUrl()).hasSize(500);
        assertThat(view.userAgent()).hasSize(500);
        assertThat(view.detail()).hasSize(8000);
    }

    @Test
    void endpointsRequireLoginAndAdmin() throws Exception {
        String userToken = jwtProvider.createAccessToken(user, "MEMBER");
        String adminToken = jwtProvider.createAccessToken(admin, "ADMIN");

        mockMvc.perform(post("/api/error-reports")
                        .header("Authorization", "Bearer " + userToken)
                        .header("User-Agent", "JUnit-UA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"USER","message":"결제 후 보석이 안 들어와요","pageUrl":"https://kiiwee.vercel.app/store"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());

        mockMvc.perform(post("/api/error-reports")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"USER\",\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/admin/error-reports").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/error-reports").param("kind", "USER")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kind").value("USER"));
    }

    private static ErrorReportService.Submission auto(String message, String pageUrl) {
        return new ErrorReportService.Submission(ErrorReportKind.AUTO, message, pageUrl, "UA",
                "at render (App.tsx:10)\nat other");
    }

    private Long member(boolean isAdmin) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member member = Member.register("err-" + unique + "@yonsei.ac.kr", "테스트", "hash", "연세대학교", Gender.MALE,
                LocalDate.of(2003, 1, 1), null, "kiwi_" + unique, Instant.now(), LocalDate.now());
        if (isAdmin) {
            ReflectionTestUtils.setField(member, "role", Role.ADMIN);
        }
        return members.saveAndFlush(member).getId();
    }
}
