# ADR 0007 — VAT posts to separate Output and Input accounts, per line, summed by rate

**Date:** 2026-07-28
**Status:** Accepted — accounts and ledger support implemented in build step 7 (migrations V14, V15);
the invoice side arrives in steps 8 and 9

Answers **Q14**, the one item in `PROGRESS.md` recorded as a real design gap in the brief rather
than a clarification.

## Decision

Four parts.

1. **Separate `Output VAT` and `Input VAT` accounts, never netted.** Output VAT is a liability
   (VAT charged on our sales, held until paid over). Input VAT is asset-side (VAT charged to us,
   reclaimable — a claim on the tax authority).
2. **VAT is computed per line and summed by rate for posting.** An invoice with 13% lines and 24%
   lines posts two Output VAT lines, one per rate, and each posted line carries the VAT class it
   was computed at and the net amount it was computed on.
3. **Reverse charge (`VatStatus.INTRA_EU_B2B`) is its own path**: self-assessed output and input
   entries for the same base, netting to zero.
4. **An exempt line posts no VAT at all** — the net amount only — and its `VatExemptionReason` is
   carried on the invoice line for documentation.

## Context

Brief §4's account list carried a single `VAT payable` account, and `V4` seeded it with a comment
saying one was almost certainly insufficient. Nothing anywhere specified how VAT actually posts.
NovoCore has no filing duty — the external accountant does — but the ledger still has to carry VAT
correctly on every purchase and sales invoice, and has to be able to hand the accountant figures
they can file from.

**Why netting is wrong, specifically.** A single account holding output minus input destroys the
two figures a VAT return is made of. Worse, it makes the case that matters most invisible: a period
where reclaimable input VAT exceeds output VAT is a *refund position*, and once the two have been
added together that is indistinguishable from a small liability. Splitting them costs one extra
account and makes the return readable straight off the trial balance.

**Why per line rather than per document.** VAT is a per-line property — the rate follows the goods,
and `VatClassPrecedence` already resolves it as invoice line beats customer beats product. Computing
per document would require a single rate per document, which is false for any mixed-rate order, and
Java Jives sells both 24% equipment and 13% food items.

**Why the rate has to be on the posted line.** This is the part that decides schema rather than
arithmetic. "Summed by rate" is only meaningful if the sum can be told apart afterwards: without the
class on the line, two Output VAT lines at different rates are two indistinguishable amounts against
one account, and one could have posted a single total and lost nothing. So `journal_line` carries
`vat_class_id` and `taxable_base`. It also cannot live only on the invoice, because a Manual Journal
Entry can post to a VAT account directly — an accountant's period adjustment is exactly that — and a
VAT figure assembled from documents alone would silently omit it.

The **class** is stored, never the rate: two seeded classes both charge 4% under different legal
bases and different myDATA codes (`1040` and `1041`), so a rate is ambiguous as an identity. That is
the same reason `VatClassService` has no `findByRate`.

## Consequences

- **`V14` repurposes `V4`'s `VAT payable`** into `Output VAT — VAT on sales` with the system key
  `OUTPUT_VAT`, and adds `Input VAT — VAT on purchases` as an asset in Current Assets with
  `INPUT_VAT`. Repurposing rather than deactivating-and-replacing is safe here for exactly one
  reason, worth stating so nobody generalises from it: **nothing had posted anywhere**, since the
  journal did not exist until `V15`. An account that has been posted to is never repurposed.

- **Neither account is `expected_to_clear`.** Both do clear each period, but that flag means "a
  residual balance here is a discrepancy", and a balance on either between filings is the ordinary
  state of affairs — it is the VAT accrued so far. Flagging them would put a permanent false positive
  into phase 8's Clearing Checks, which is how a check stops being read.

- **There is no third "VAT payable to authorities" account.** Settling a period is a movement the
  chart already expresses: debit Output VAT, credit Input VAT, and the net against the bank. A third
  account would only be needed to accrue the return before paying it, and NovoCore never accrues one.

- **The VAT dimension is permitted, not required, on the two VAT accounts, and forbidden
  everywhere else.** Required would break that settlement, which moves money at no rate at all.
  Forbidden elsewhere stops a rate being attached to a revenue line where nothing would ever read it.
  Enforced by a trigger as well as by the service.

- **Reverse charge needs no new structure.** It is two lines in one ordinary entry — debit Input VAT,
  credit Output VAT, same class, same base — so it balances by itself and nets to zero in cash while
  both figures stay separately reportable, which is exactly what netting the accounts would have
  destroyed.

- **`JournalService.vatTotals(from, to)`** reads the dimension back, grouped by direction and class,
  netted per direction so that a credit note reduces output VAT rather than appearing as a second
  figure. It exists in step 7 rather than with the reports because a column nothing reads is
  indistinguishable from a column nobody thought about. It is a query, not a report: phase 8's VAT
  report and phase 7's myDATA adapter both read it.

- **Posted VAT and "the rate applied to the whole base" will differ by a cent or two**, and that is
  arithmetic rather than error — the posted figure is a sum of per-line roundings.
  `VatTotal.roundingDivergence()` exists so the difference is visible, because a gap of euros rather
  than cents means something posted at the wrong rate.

- **Step 9 obligations.** The Sales Invoice and Purchase Invoice postings must supply the VAT
  dimension on every VAT line — nothing forces them to, since the dimension is optional at the
  ledger. Exempt lines must carry their `VatExemptionReason` on the invoice line, and exempt turnover
  by reason is therefore a document-level report, not a ledger-level one.

- **Phase 7 obligation, unchanged:** `VatExemptionReason.mydataCode` is nullable for the OSS/IOSS
  reasons and transmission must refuse a NULL rather than compose a substitute (Q36).
