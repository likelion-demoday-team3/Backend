# Voting and matching

Depends on the hearts PR. Merge parent PRs in order.

- POST /api/sessions/{sessionId}/votes: { rankedMemberIds: [memberId1, memberId2, memberId3] }
- GET /api/sessions/{sessionId}/votes/me: data { submitted, rankedMemberIds }
- GET /api/sessions/{sessionId}/results/summary: data { sessionId, coupleCount }

All use JWT and the common ApiResponse wrapper.
An empty list is a valid submitted pass, distinct from never submitting.
Votes are final on submission. An identical retry returns the original result; a changed retry is rejected.
Only distinct, attended opposite-gender participants are allowed. Deadline is exclusive.
Submission records votedAt and applies the configured completion reward once through GemWallet.

At the deadline the server closes voting, matches mutual first choices only, and persists normalized pairs.
A zero-vote session completes with zero couples.
Clients receive /user/queue/live/results-ready: { sessionId, coupleCount } after commit.
No other people's rankings are included in this free response. Mutual first-choice
partners receive automatic contact-sharing grants after matching; details are in
contact-history-api.md.

No new vote/match tables are introduced: they already exist in the initial migration.
