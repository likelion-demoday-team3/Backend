package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.PartnerProfileCard;
import com.example.kiwi_backend.contract.dto.CompatibilitySummary;

public record LiveRoundAssignmentMessage(
        Long sessionId,
        Long roundId,
        int roundNumber,
        Long roomId,
        int tableNo,
        Long memberId,
        Long partnerMemberId,
        PartnerProfileCard partnerProfileCard,
        CompatibilitySummary compatibility
) {
}
