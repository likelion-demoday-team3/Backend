# Hearts

Base branch: feat/live-scheduler-integration (merge its PR first).

After all rounds complete, clients receive /user/queue/live/hearts-started:
{ sessionId, heartsEndsAt }. Move to the lobby and stop the previous voice connection.
The backend opens a 60-second window. It rejects requests at or after the deadline,
even when the periodic transition has not run yet. Voting opens for 120 seconds after hearts close.
After voting closes, mutual first choices are matched and the free couple count is sent.

## HTTP (JWT required)

- POST /api/sessions/{sessionId}/hearts: { receiverId }
  data: { receiverId, chargedGem, alreadySent }
- GET /api/sessions/{sessionId}/hearts
  data: { sentTo, receivedCount, freeHeartRemaining, heartsEndsAt }

Common wrapper: { success, data, error }.
Only attended opposite-gender participants in this session are valid targets.
First heart per sender per session is free; further distinct recipients cost one gem each.
A duplicate request returns the existing receipt, without another debit or message.
The server sets the price; the client never supplies a price or sender ID.

## Notifications

- /user/queue/live/heart-received: { sessionId, receivedCount }
- /user/queue/live/voting-started: { sessionId, votingEndsAt }

Sender identity is not exposed in received-heart notifications.
Messages are sent after commit. GET restores the caller's current heart totals.

## Wallet coordination

Adds HEART_SEND (debit, SESSION reference) to the shared GemTransactionType contract.
Calls existing GemWallet.apply in the same transaction with a stable sender/recipient key.
Wallet balance logic is unchanged; the history display adds a HEART_SEND label.
Insufficient balance rolls back the heart.

## Limits

H2 tests do not replace PostgreSQL migration/concurrency verification.
