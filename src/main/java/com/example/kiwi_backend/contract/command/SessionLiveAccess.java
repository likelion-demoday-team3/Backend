package com.example.kiwi_backend.contract.command;

import com.example.kiwi_backend.contract.dto.LiveSessionContext;
import java.time.Instant;
import java.util.List;

public interface SessionLiveAccess {
    /** Must be called inside the caller's transaction. Locks session before wallet or interaction rows. */
    LiveSessionContext lock(Long sessionId);
    Instant startHearts(Long sessionId, Instant now);
    List<Long> findHeartSessionsDue(Instant now);
}
