package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOneIdentityVerificationServiceTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final Member member = Member.register("test@yonsei.ac.kr", "hash", "연세대학교",
            Gender.FEMALE, LocalDate.of(2002, 1, 1), "01012345678", null,
            Instant.parse("2026-10-01T00:00:00Z"), LocalDate.of(2026, 10, 13));
    private final PortOneIdentityVerificationService service = new PortOneIdentityVerificationService(
            members, restClientBuilder, Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "apiSecret", "portone-test-secret");
        ReflectionTestUtils.setField(service, "storeId", "store-test");
        ReflectionTestUtils.setField(service, "channelKey", "channel-test");
        member.beginIdentityVerification("iv123");
        when(members.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(members.existsByPortoneIdentityVerificationId("iv123")).thenReturn(false);
    }

    @Test
    void createsVerificationIdBoundToTheCurrentMember() {
        var started = service.start(1L);

        assertThat(started.storeId()).isEqualTo("store-test");
        assertThat(started.channelKey()).isEqualTo("channel-test");
        assertThat(started.identityVerificationId()).matches("[A-Za-z0-9]{32}");
        assertThat(member.getPendingPortoneIdentityVerificationId()).isEqualTo(started.identityVerificationId());
    }

    @Test
    void trustsOnlyVerifiedProviderDataAndPersistsVerificationId() {
        server.expect(requestTo("https://api.portone.io/identity-verifications/iv123"))
                .andExpect(header("Authorization", "PortOne portone-test-secret"))
                .andRespond(withSuccess("""
                        {"status":"VERIFIED","verifiedCustomer":{"gender":"FEMALE","birthDate":"2002-01-01"}}
                        """, APPLICATION_JSON));

        var result = service.verify(1L, "iv123");

        assertThat(result.verified()).isTrue();
        assertThat(result.verifiedAt()).isEqualTo(Instant.parse("2026-10-13T12:00:00Z"));
        assertThat(member.getPortoneIdentityVerificationId()).isEqualTo("iv123");
        server.verify();
    }

    @Test
    void rejectsProviderIdentityThatDoesNotMatchSignupProfile() {
        server.expect(requestTo("https://api.portone.io/identity-verifications/iv123"))
                .andRespond(withSuccess("""
                        {"status":"VERIFIED","verifiedCustomer":{"gender":"MALE","birthDate":"2002-01-01"}}
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> service.verify(1L, "iv123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가입 정보와 본인인증 정보가 일치하지 않습니다");
        assertThat(member.getIdentityVerifiedAt()).isNull();
        server.verify();
    }

    @Test
    void rejectsUnverifiedProviderStatus() {
        server.expect(requestTo("https://api.portone.io/identity-verifications/iv123"))
                .andRespond(withSuccess("{\"status\":\"READY\"}", APPLICATION_JSON));

        assertThatThrownBy(() -> service.verify(1L, "iv123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PortOne 본인인증이 완료되지 않았습니다");
        server.verify();
    }
}
