package com.example.kiwi_backend.realtime.dto;

/**
 * 재입장 응답에 담는 지난 대화 상대. 새로고침·기기 변경 후에도 하트·투표 후보를 되살리는 데 쓴다.
 * alias는 세션 안에서 보여줄 상대 별칭이며, 조회하지 못하면 null.
 */
public record LiveJoinPartner(
        Long memberId,
        String alias,
        int roundNumber
) {
}
