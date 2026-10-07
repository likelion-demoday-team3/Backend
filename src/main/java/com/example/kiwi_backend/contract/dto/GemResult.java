package com.example.kiwi_backend.contract.dto;

/**
 * 보석 변동 결과.
 * alreadyProcessed가 true면 같은 멱등 키로 이미 처리된 요청이라 이번엔 아무것도 바뀌지 않았다는 뜻.
 */
public record GemResult(
        Long historyId,
        int balanceAfter,
        boolean alreadyProcessed
) {
}
