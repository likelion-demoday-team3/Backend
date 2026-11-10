package com.example.kiwi_backend.contract.dto;

import java.util.List;

public record PartnerProfileCard(
        Long memberId,
        String university,
        /** 학교 지역 (시·도). 모르면 null */
        String region,
        String idealType,
        String datingStyle,
        String contactStyle,
        String loveStyle,
        List<String> interests
) {
}
