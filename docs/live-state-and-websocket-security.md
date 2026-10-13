# Live state and WebSocket access

## Restore live state after reconnect

- GET `/api/sessions/{sessionId}/live/state`
- Requires a JWT belonging to an attendee marked `ATTENDED` in this session.
- Uses the common `{ success, data, error }` response.
- `data`: `{ sessionId, phase, deadline, voteSubmitted, participants }`.
- Each participant contains only `{ memberId, gender, alias }`.

The endpoint lets the frontend restore the current HEART, VOTING, or result phase,
show the current deadline, rebuild the attendee list, and know whether the caller
already submitted a vote. It does not return anyone else's votes, paid results,
or contact values. Use the existing hearts status, own-vote, results summary,
and window endpoints to reload their respective details.

## STOMP destinations

Connect to `/ws` with `Authorization: Bearer <access-token>` in the STOMP CONNECT
frame. The configured `app.cors.allowed-origins` values also restrict browser origins.

Clients may SEND only to `/app/live/join` and `/app/live/signal`.
Clients may SUBSCRIBE only to their own `/user/queue/live/...` destinations:
`joined`, `signal`, `round-assigned`, `round-idle`, `voting-started`,
`starting-soon`, `cancelled`, `hearts-started`, `heart-received`, `results-ready`,
`contact-received`, `contact-requested`, and `contact-request-answered`.

Direct SEND to broker destinations, public topic subscriptions, raw queue subscriptions,
and subscriptions naming another user's id are rejected.
