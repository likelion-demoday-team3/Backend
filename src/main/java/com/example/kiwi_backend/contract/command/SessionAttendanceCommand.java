package com.example.kiwi_backend.contract.command;

import java.util.Set;

/** session module marks active applicants in the snapshot ATTENDED, and other applicants NO_SHOW. */
public interface SessionAttendanceCommand {

    void finalizeAttendance(Long sessionId, Set<Long> connectedMemberIds);
}
