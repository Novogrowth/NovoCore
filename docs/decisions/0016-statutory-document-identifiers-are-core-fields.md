# 0016 — ΜΑΡΚ, UID, QR URL and transmission status are core fields; a vendor's document id is not

**Status:** Accepted, 2026-08-02
**Amends:** the "external system reference IDs never live on core entities" rule — `CLAUDE.md`
non-negotiable rule 2, and the brief's *field boundary rule*. It does not weaken either; it draws the
line those rules were always about.

## Context

Greek law requires every sales document to be transmitted to AADE at the moment it is issued. AADE
answers with a **ΜΑΡΚ** (Μοναδικός Αριθμός Καταχώρησης) and a **QR code URL**, and the transmission
carries a **UID**. Novocore does not perform that transmission — Prosvasis Go does today, and a
certified Πάροχος will at step 40 — so a sales document appears in Novocore only *after* it legally
exists, already carrying these values.

That produces a question the existing rule answers wrongly if read literally. Rule 2 says external
system reference IDs never live on core entities, and each adapter keeps its own mapping table. The
ΜΑΡΚ arrives *through* an adapter. Read mechanically, it belongs in the Go adapter's mapping table.

## Decision

**ΜΑΡΚ, UID, QR URL and transmission status are core fields on the sales invoice.** They live on the
core record, are defined by the core's own needs, and are never delegated to an adapter mapping table.

**Prosvasis Go's own internal document id stays in the Go adapter's mapping table**, exactly as rule 2
requires — as does WooCommerce's order id, Skroutz's, and every other vendor's.

## Why the two are categorically different

Rule 2 exists to stop the core's data model being shaped by whatever schema an external system happens
to have. The test it encodes is: **would this field survive the external system being replaced?**

- **Go's document id would not.** It identifies a row in Go's database. Replace Go with a Πάροχος at
  step 40 and every one of those ids becomes meaningless — which is precisely why they belong in a
  mapping table that gets retired with the adapter that owns it.
- **The ΜΑΡΚ would.** It is a **statutory identifier of Novocore's own document**, assigned by the tax
  authority, cited in a VAT return, and the thing an auditor asks for. It survives Go being replaced,
  survives the Πάροχος being changed at step 40, and survives Novocore itself. Storing it in an
  adapter's mapping table would mean a legally required identifier of our own document disappearing
  when we swap a vendor.

The same reasoning already put **myDATA codes in the core** — on `VatExemptionReason`, on
`UnitOfMeasure` — for exactly this reason, so this ADR is a restatement of a line already drawn rather
than a new exception. **The distinguishing question is not "did it arrive through an adapter?" but
"whose record does it identify, and does it outlive the vendor?"**

## Consequences

- The sales invoice schema carries these fields from **R1** onward. They are nullable until a
  transmission has happened, and the transmission status is what says which.
- **They are not written by Novocore's own logic.** Novocore never obtains a ΜΑΡΚ itself (see
  `CLAUDE.md`, *The document model*); it records the one that came back. That remains true at step 40,
  where Novocore begins composing and numbering the document but still transmits through the Πάροχος.
- **Transmission status is a core concern, not an adapter concern.** "Recorded but not yet transmitted"
  and "transmitted, ΜΑΡΚ received" are states of Novocore's document that reports and Clearing Checks
  must be able to query without asking an adapter.
- Series gap detection (step 25) depends on this: because Novocore records numbers rather than
  generating them, a gap in a series means a document was issued through Go and never arrived — a check
  that is only possible because the numbering is observed and stored on the core record.
- ⚠️ **A future reader tempted to "clean this up" by moving these to the adapter should read the test
  above rather than the rule alone.** The rule's wording is mechanical; its purpose is not.
