package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.member.api.dto.IdentityVerificationResponse;
import com.example.kiwi_backend.member.api.dto.IdentityVerificationStartResponse;
import com.example.kiwi_backend.member.domain.entity.Member;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortOneIdentityVerificationService {

    private final MemberRepository memberRepository;
    private final RestClient.Builder restClientBuilder;
    private final Clock clock;

    @Value("${app.portone.api-secret:}")
    private String apiSecret;

    @Value("${app.portone.store-id:}")
    private String storeId;

    @Value("${app.portone.identity-channel-key:}")
    private String channelKey;

    @Transactional
    public IdentityVerificationStartResponse start(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다"));
        if (storeId == null || storeId.isBlank() || channelKey == null || channelKey.isBlank()) {
            throw new IllegalStateException("PortOne store ID와 본인인증 channel key를 설정해야 합니다");
        }
        String verificationId = UUID.randomUUID().toString().replace("-", "");
        member.beginIdentityVerification(verificationId);
        return new IdentityVerificationStartResponse(storeId, channelKey, verificationId);
    }

    @Transactional
    public IdentityVerificationResponse verify(Long memberId, String verificationId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다"));
        if (verificationId == null || !verificationId.matches("[A-Za-z0-9]{1,40}")) {
            throw new IllegalArgumentException("본인인증 ID 형식이 올바르지 않습니다");
        }
        if (member.getIdentityVerifiedAt() != null) {
            member.verifyIdentity(member.getIdentityVerifiedAt(), verificationId);
            return new IdentityVerificationResponse(true, member.getIdentityVerifiedAt());
        }
        if (!verificationId.equals(member.getPendingPortoneIdentityVerificationId())) {
            throw new IllegalArgumentException("이 계정에서 시작한 본인인증이 아닙니다");
        }
        if (memberRepository.existsByPortoneIdentityVerificationId(verificationId)) {
            throw new IllegalStateException("이미 다른 계정에서 사용한 본인인증입니다");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("PORTONE_API_SECRET을 설정해야 본인인증을 확인할 수 있습니다");
        }

        JsonNode verification = restClientBuilder.build()
                .get()
                .uri("https://api.portone.io/identity-verifications/" + verificationId)
                .header("Authorization", "PortOne " + apiSecret)
                .retrieve()
                .body(JsonNode.class);
        verifyCustomerMatchesMember(verification, member);
        Instant verifiedAt = clock.instant();
        member.verifyIdentity(verifiedAt, verificationId);
        return new IdentityVerificationResponse(true, verifiedAt);
    }

    private void verifyCustomerMatchesMember(JsonNode verification, Member member) {
        if (verification == null || !"VERIFIED".equals(verification.path("status").asText())) {
            throw new IllegalArgumentException("PortOne 본인인증이 완료되지 않았습니다");
        }
        JsonNode customer = verification.path("verifiedCustomer");
        String genderValue = customer.path("gender").asText();
        String birthDateValue = customer.path("birthDate").asText();
        try {
            Gender verifiedGender = Gender.valueOf(genderValue);
            LocalDate verifiedBirthDate = LocalDate.parse(birthDateValue);
            if (verifiedGender != member.getGender() || !verifiedBirthDate.equals(member.getBirthDate())) {
                throw new IllegalArgumentException("가입 정보와 본인인증 정보가 일치하지 않습니다");
            }
        } catch (IllegalArgumentException e) {
            if ("가입 정보와 본인인증 정보가 일치하지 않습니다".equals(e.getMessage())) {
                throw e;
            }
            throw new IllegalArgumentException("PortOne에서 유효한 성별·생년월일을 확인할 수 없습니다");
        }
    }
}
