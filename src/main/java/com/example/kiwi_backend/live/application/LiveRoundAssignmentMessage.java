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
        // 세션 안에서 보여줄 상대 별칭 (예: "졸린 수달"). 조회할 수 없으면 null
        String partnerAlias,
        PartnerProfileCard partnerProfileCard,
        CompatibilitySummary compatibility
) {
}
