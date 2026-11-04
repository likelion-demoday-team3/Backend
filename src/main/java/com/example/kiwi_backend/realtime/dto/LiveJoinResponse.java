package com.example.kiwi_backend.realtime.dto;

import com.example.kiwi_backend.contract.dto.PartnerProfileCard;
import java.time.Instant;
import java.util.List;

/**
 * 라이브 입장(재접속 포함) 응답.
 * 라운드 중 재접속(ASSIGNED)이면 배정 메시지를 다시 받지 못하므로, 상대 별칭과 프로필 카드도 함께 돌려준다.
 * partners는 이미 시작했거나 끝난 라운드에서 만난 상대 전부(현재 라운드 포함, 라운드 순)이다.
 */
public record LiveJoinResponse(
        Long sessionId,
        Long memberId,
        LiveJoinStatus status,
        Long roundId,
        Integer roundNumber,
        Long roomId,
        Integer tableNo,
        Long partnerMemberId,
        String partnerAlias,
        PartnerProfileCard partnerProfileCard,
        Instant roundEndsAt,
        List<LiveJoinPartner> partners
) {
}
