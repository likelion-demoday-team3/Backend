# Live scheduler integration

## Flow

- Poll every 5 seconds, independent of WebSocket join.
- At start time: freeze lobby attendance, cancel/refund or persist the rotation.
- At start time + 15 seconds: start the first round.
- Regular sessions and admin call-test sessions use the same timing (DatingSession.LOBBY_CLOSE_AFTER, FIRST_ROUND_AFTER).
- Session state and first round state change in one transaction under the session lock.
- Send lobby notices and round assignments only after commit.
- Failed lobby/database work remains eligible for the next poll.

## Frontend

Subscribe before joining:

- /user/queue/live/starting-soon: { sessionId, firstRoundAt } (UTC timestamp)
- /user/queue/live/cancelled: { sessionId, reason: "INSUFFICIENT_ATTENDANCE" }

POST /api/live/sessions/{sessionId}/start is an admin command. It now returns
{ success: true, data: null, error: null }; this acknowledges processing, not an immediate round start.
Participant clients must not call manual round/scheduler control APIs.

## Limits and follow-up

- Presence and subsequent round timers are still process-local. This is NOT full restart recovery or a multi-instance presence solution.
- Post-commit notifications are best effort, not an outbox with guaranteed retries.
- Compatibility/profile availability is still required for round assignment messages.
- Heart/vote/result/contact features are separate PRs; the existing end-of-round voting behavior remains until the heart PR.
- H2 tests verify JPA transactions. They do not verify PostgreSQL Flyway migrations.

## Coordination

This replaces the proposed feat/changes-after-scheduler changes on top of current main.
Do not merge the unfinished proposal as a second implementation.
SessionLiveStatusCommand.markRoundsStarted now rejects early or duplicate starts and
must be called in the same transaction as the first round update.
