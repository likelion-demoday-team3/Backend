package com.example.kiwi_backend.contract.dto;

import java.util.List;

public record PartnerProfileCard(
        Long memberId,
        String university,
        String idealType,
        String datingStyle,
        String contactStyle,
        String loveStyle,
        List<String> interests
) {
}
