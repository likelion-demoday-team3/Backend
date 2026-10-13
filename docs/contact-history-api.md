# Contacts and history

Depends on the voting/matching PR. All HTTP endpoints require JWT and use ApiResponse.
The current caller is taken from authentication, never the body.

## Window sequence

- GET /api/sessions/{sessionId}/windows -> { resultsClosed, contactsClosed }
- POST /api/sessions/{sessionId}/results/close (no body)
- POST /api/sessions/{sessionId}/contacts/close (no body)

Closure is persisted and irreversible. Results must close before sending/requesting contacts.
After contact closure, new outgoing actions are rejected. Existing incoming requests can still
be answered from history without reopening the closed window.

## Sending and requesting

- POST /api/sessions/{sessionId}/contacts/send
- POST /api/sessions/{sessionId}/contacts/requests

Body: { targetId, contactType: "PHONE" | "INSTAGRAM" }.
Only opposite-gender attendees in the same completed session are eligible.
Matched partners are excluded from paid actions; their contact is automatically shared free.
Automatic sharing uses PHONE when available, otherwise INSTAGRAM.
An unavailable/deleted contact is not invented or charged for.

Sending costs one gem immediately. Requesting is free until accepted.
One request/share per directed pair per session; repeated requests return the existing record.

- POST /api/sessions/{sessionId}/contacts/requests/{requestId}/response
  Body: { accept: true | false }

Only the target may answer. Acceptance debits the REQUESTER one gem atomically with granting access.
Insufficient balance leaves the request pending and does not reveal the contact.
Rejection is free. Repeating a response does not charge again.
If the contact was already shared voluntarily, acceptance does not charge again.

## History

- GET /api/contact-history/shares?page=0&size=20
- GET /api/contact-history/requests?page=0&size=20

data: { items, hasNext }. Maximum size 50. Each endpoint only returns the caller's sent/received records.
Contact values are not copied into the history database: the sharing grant allows lookup of the
currently registered value of the granted type. Inactive/withdrawn accounts yield null contactValue.
This avoids duplicating contact PII and means later contact changes are reflected in history.

## WebSocket notifications

Subscribe to /user/queue/live/contact-received ({ sessionId, shareId }),
/user/queue/live/contact-requested, and /user/queue/live/contact-request-answered.
Fetch history for the authoritative state; notifications do not contain contact values.

## Wallet coordination

Adds CONTACT_SEND and CONTACT_REQUEST debit types/history labels.
Uses the existing GemWallet transaction and stable operation keys.
No payment gateway or wallet-balance implementation is added.
