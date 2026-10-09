package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.CompatibilitySummary;

/** Reads compatibility calculated before the live round; it must not call AI during broadcast. */
public interface CompatQueryService {

    CompatibilitySummary getCompatibility(Long sessionId, Long memberAId, Long memberBId);
}
