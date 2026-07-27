# Adapters — reserved, intentionally empty

This directory is a placeholder. **Nothing belongs here in Phase 1.**

An *adapter* translates an external system into NovoCore's own model. Each one is a
separate Maven submodule added to `pom.xml` when its phase begins.

## Rules for anything added here

These are not style preferences — they are `CLAUDE.md` architecture rules 2, 3, 4 and 8,
and the ArchUnit suite in `../architecture-tests` fails the build when they are broken.

1. **Depend on `novocore-core-api` only.** Never on `novocore-core`. The core's JPA
   entities and repositories are not on your compile classpath, and that is deliberate —
   if you need something the core does not expose, add the method to the core's service
   interface. Do not reach around it.
2. **No database access.** No `DataSource`, no JDBC, no Spring Data repository, no SQL.
   The one exception is your own external-ID-to-core-ID mapping table (see rule 3), which
   is yours alone and is never read by the core.
3. **External reference IDs live here, never on core entities.** A Go product ID or Woo
   product ID belongs in this adapter's own mapping table. The core knows only its own
   SKU/ID.
4. **Outbound calls are asynchronous.** A core operation must never block on an external
   API. Pushing to Go or notifying Woo happens in the background.
5. **Contract tests are mandatory.** Every adapter needs an automated test asserting the
   external API still returns the shape this adapter expects. On an unexpected shape,
   fail loudly and flag it — never silently drop or guess at malformed data.
6. **Never webhook-only.** Pair webhooks with pull-based reconciliation, log every call's
   outcome, and alert on stale sync timestamps.

## Planned adapters and their phases

| Adapter | Phase | Purpose |
|---|---|---|
| WooCommerce | 3 | Store adapter; absorbs the fragile plugin-based order/stock/voucher sync |
| Prosvasis Go | 3 | Stock/cost, PO documents, invoice issuing + myDATA + POS triggering. **Transitional** — retired at phase 11 |
| File import (Excel/CSV) | 5 | Clearing reconciliation files, Manager.io migration |
| Bank aggregator | 6 | Read-only PSD2 balances. No payment initiation. Provider selection still open |
| AADE myDATA | 7 | Cross-check plus structured invoice source |
| AADE/VIES lookup | 7 | Auto-fill customer/supplier data from a VAT number |
| ACS Courier | 8-ish | Clearing plus operational voucher/label generation |
| Skroutz | 8-ish | Clearing plus receiving/printing Skroutz's own voucher |
| POS provider (ePay/Piraeus) | 8-ish | Clearing, including commission analysis |
| AADE Provider (Πάροχος) | 11 | Alternative myDATA transmission. Needs accountant confirmation |
| POS terminal | 11 | Triggers card payment on the physical terminal |

Phases for the clearing adapters are approximate — brief §10 sequences Clearing Checks at
phase 8, and each partner adapter is needed by then.
