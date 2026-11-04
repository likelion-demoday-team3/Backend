package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.query.LiveEncounterQuery;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveEncounterQueryService implements LiveEncounterQuery {

    /** 이미 만난 상대로 칠 라운드. PENDING 라운드는 방이 미리 짜여 있을 뿐 아직 만나지 않았다 */
    private static final List<LiveRoundStatus> MET_ROUND_STATUSES =
            List.of(LiveRoundStatus.IN_PROGRESS, LiveRoundStatus.COMPLETED);

    private final LiveRoomRepository liveRoomRepository;

    @Override
    public boolean haveMet(Long sessionId, Long memberId, Long otherMemberId) {
        if (sessionId == null || memberId == null || otherMemberId == null || memberId.equals(otherMemberId)) {
            return false;
        }
        return liveRoomRepository.existsMetPair(sessionId, memberId, otherMemberId, MET_ROUND_STATUSES);
    }
}
