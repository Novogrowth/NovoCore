# Modules — reserved, intentionally empty

This directory is a placeholder. **Nothing belongs here in Phase 1.**

A *module* is internally-driven functionality built on top of the core — as opposed to an
adapter, which translates an external system. Each module is a separate Maven submodule
added to `pom.xml` when its phase begins.

## Rules for anything added here

Same boundary as adapters (`CLAUDE.md` rule 3), enforced by the ArchUnit suite in
`../architecture-tests`:

1. **Depend on `novocore-core-api` only**, never on `novocore-core`.
2. **No database access.** Modules with genuinely module-local state own their own tables,
   but never read or write the core's.
3. **Never re-implement a shared core service.** Email sending and document attachments
   are core-owned services exposed through a single interface each. A module that
   configures its own SMTP credentials recreates precisely the scattered-credentials
   problem that rule exists to prevent.

## Planned modules and their phases

| Module | Phase | Purpose |
|---|---|---|
| Purchase Orders | 4 | Supplier PO automation; pushes to Go |
| Sales Order Fulfillment | 4 | Order screen; channel-dependent vouchers; QZ Tray silent printing |
| Reports | 8 | On-demand reporting |
| Clearing Checks | 8 | Reconciles invoices against each partner's own records; built on open item matching |
| Product Creator | 9 | Woo products from a barcode, a supplier link, or manual entry. Once built, NovoCore becomes the point of product creation, syncing outward |
| Roast Date Report | 9 | Coffee stock sorted by roast date |
| Back-in-Stock Reminders | 9 | Manual call queue |
| Service/Technician Management | 9 | Repair department; ties to serial numbers |
| Price Tag Printing | 9 | Existing standalone tool, folded in |
| Accountant Monthly Package | 9 | The accountant's monthly document set — the project's original motivating example |
| AI Analysis | 10 | Read-only Q&A via the Claude API, deterministic queries only. Sequenced last |
| Employee Digital Work-Card (Ergani) | *tentative* | Law 4808/2021 obligation. Needs accountant confirmation |

**Barcode scanning is not a module.** It is an input mechanism used across Purchase
Invoice/Goods Receipt verification, Sales Order picking, and in-store sales, built on the
existing EAN and serial number data.
