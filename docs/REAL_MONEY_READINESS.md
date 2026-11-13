# Real-Money Readiness — EX1

> **Status: NOT IMPLEMENTED / NOT ENABLED.** This document is a future planning specification only. EX1 currently supports non-purchasable, non-withdrawable demo points. It does not accept deposits, collect paid room entries, hold customer funds, or pay cash prizes.

## 1. Non-negotiable launch gates

Real-money features must remain unavailable until **all** of the following have been independently confirmed:

- The business/operator is legally eligible to run the service and has the required registrations, licences, and contracts.
- A qualified lawyer has reviewed the exact operating countries, tournament format, prize model, consumer terms, age restrictions, tax treatment, and payment flow.
- A regulated payment provider has approved the business model in writing and supports the required marketplace or split-settlement arrangement.
- The provider's required identity, age, sanctions, fraud, and anti-money-laundering checks are in place.
- Refunds, cancellations, disputes, chargebacks, account closure, unclaimed prizes, and customer complaints have documented procedures.
- Independent security and accounting reviews have been completed.
- The operator is an adult and is authorized to operate the business. Adult participants alone do not satisfy this requirement.

Until every gate is met, no production payment credentials, cash balances, deposit/withdrawal controls, paid-entry controls, or prize-payment controls may be enabled.

## 2. Intended future architecture (conceptual only)

If the model is approved in the future, it should use a licensed payment provider's marketplace / connected-account product or another legally approved safeguarded-funds arrangement.

Conceptual separation:

1. **Payment provider:** processes customer payments and performs the checks it is contractually responsible for.
2. **Customer balance records:** show only amounts the provider confirms are available and legally attributable to the customer. These records must never be treated as money held by EX1 unless the legal structure explicitly permits that.
3. **Tournament accounting:** records approved entry, refund, cancellation, and prize obligations as distinct events.
4. **Business settlement:** receives only amounts the provider is permitted to settle to the business under the approved agreement.
5. **Prize settlement:** sends approved prizes through the provider's supported payout process, subject to eligibility and verification.
6. **Reconciliation:** compares EX1's accounting records with provider statements and settlement reports.

**Do not route participant funds into an owner's personal wallet or personal bank account.** Do not represent an internal database balance as a bank account, stored-value account, or guaranteed withdrawable balance.

## 3. Accounting and audit principles

A future implementation would require a professionally reviewed, immutable double-entry accounting design:

- Every posted financial event must balance; debits and credits must reconcile.
- Posted entries must not be edited or deleted. Corrections must be represented by linked reversing and replacement entries.
- Every event must have a unique idempotency key to prevent duplicate processing.
- Provider transaction IDs, settlement IDs, currency, timestamps, status, and references must be retained for reconciliation.
- Pending, available, reserved, disputed, refunded, and settled amounts must never be conflated.
- Balances must be derived from the ledger, not manually overwritten.
- Financial actions must have strong authorization, separation of duties, audit trails, and independent reconciliation.
- Store the minimum personal data necessary; use the provider for sensitive identity verification wherever possible.

This document intentionally does **not** define production tables, SQL, API routes, payment webhooks, or executable money-movement logic.

## 4. EX1's current safe mode

Until the launch gates above are met:

- Keep the existing demo-points model only.
- Demo points cannot be purchased, transferred, withdrawn, redeemed, or exchanged for prizes or cash.
- Tournament entry and prize fields must remain explicitly labelled as demo points.
- Do not connect a live payment gateway.
- Do not create cash deposit, withdrawal, transfer, pooled-funds, or payout actions.
- Do not describe demo points as a wallet containing real money.

## 5. Review checklist before any future implementation

- [ ] Adult legal operator and business entity confirmed
- [ ] Jurisdiction-specific legal opinion obtained
- [ ] Payment provider underwriting and written approval obtained
- [ ] Licensing / registration requirements satisfied
- [ ] Age, identity, sanctions, fraud, and AML controls approved
- [ ] Consumer terms, privacy notice, refunds, disputes, and complaints process approved
- [ ] Tax and accounting treatment confirmed by qualified professionals
- [ ] Security threat model and independent assessment completed
- [ ] Double-entry ledger and reconciliation design reviewed by a qualified accountant and engineer
- [ ] Production launch explicitly approved by the responsible adults and provider

**This checklist is a planning aid, not legal or financial advice.**
