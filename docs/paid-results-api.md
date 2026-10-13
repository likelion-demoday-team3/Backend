# Paid results

Depends on contact/history PR. All routes require JWT and an OPEN results window.

| Product | Gems | Target |
| --- | --- | --- |
| FIRST_CHOOSERS | 1 | none |
| MY_FIRST_CHOICE_RANK | 1 | none |
| USER_RANKINGS | 2 | chosen participant |
| BUNDLE | 3 | chosen participant, not necessarily my first choice |
| ALL_RANKINGS | 10 | none |

- GET /api/sessions/{sessionId}/results/products: product catalog
- POST /api/sessions/{sessionId}/results/purchases: { product, targetId? }
- GET /api/sessions/{sessionId}/results/items?product=...&targetId=...

The client never sends price or buyerId. The server atomically charges and records access.
Repeated purchase of the same entitlement is free (chargedGem=0, newPurchase=false).
Bundle includes FIRST_CHOOSERS, MY_FIRST_CHOICE_RANK and USER_RANKINGS for the selected target.
Bundle ownership grants those component views; ALL_RANKINGS grants all views.
Partial bundle upgrades still use the advertised bundle price; there is no additional discount policy.
If all bundle components are already owned, the bundle view is free.
MY_FIRST_CHOICE_RANK/BUNDLE are unavailable if the buyer submitted no first choice; no debit occurs.

Responses contain only fields for the requested product:
firstChoosers, myRank, rankings. Other fields are empty/null.
rankOfMe=null means not selected; submitted distinguishes abstention from no submission.
Rankings use session aliases, not private member profiles or contact details.
ALL_RANKINGS includes all attendees and their submitted status/ordered choices.

After POST /results/close, paid results cannot be reopened (including previously purchased ones).
Contact history remains available separately. Wallet history retains the purchase receipt.
