package com.example.kiwi_backend.session.domain.policy;

import java.time.Duration;
import java.time.Instant;

/**
 * 본인 취소 환불 규칙.
 *
 * 시작까지 남은 시간이
 *   24시간 이상 → 100%
 *   12시간 이상 → 50%, 소수점 올림 (15 → 8)
 *   12시간 미만 → 0%
 * 경계 시각은 높은 쪽에 포함한다 (정확히 24시간 전 취소 = 100%).
 *
 * 세션 자체가 취소될 때(인원 미달, 운영자 취소)는 이 규칙과 상관없이 항상 100% 환불.
 */
public final class RefundPolicy {

    static final Duration FULL_REFUND_BEFORE = Duration.ofHours(24);
    static final Duration HALF_REFUND_BEFORE = Duration.ofHours(12);

    private RefundPolicy() {
    }

    public static int refundAmount(int entryFee, Instant startTime, Instant cancelledAt) {
        if (entryFee <= 0) {
            return 0;
        }
        Duration remaining = Duration.between(cancelledAt, startTime);
        if (remaining.compareTo(FULL_REFUND_BEFORE) >= 0) {
            return entryFee;
        }
        if (remaining.compareTo(HALF_REFUND_BEFORE) >= 0) {
            return (entryFee + 1) / 2;   // 정수 올림
        }
        return 0;
    }
}
