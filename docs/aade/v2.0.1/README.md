# AADE myDATA specification artefacts — v2.0.1

**What this folder is.** The official AADE technical specification for myDATA, version 2.0.1, exactly
as published. Novocore seeds its statutory codifications from these files and from nothing else.

**Why it is in the repository at all.** AADE publishes **no live API for codifications.** The myDATA
REST API moves documents; it does not serve the code lists. Those exist only as annex tables inside
these PDFs, as enumerations inside the XSDs, and as the classification-combination spreadsheet. So the
only way a seed can have provenance is for the artefacts it was read from to sit beside it, versioned.

**These files are never edited.** Not corrected, not reformatted, not partially extracted. If something
in them is wrong or unclear, that goes in a note here — the artefact stays as AADE shipped it. A file
that has been touched can no longer answer the question it exists to answer.

---

## Version

| | |
|---|---|
| Specification version | **v2.0.1** |
| Published by AADE | March 2026 |
| Downloaded | 2026-08-03 |
| Source page (EN) | https://www.aade.gr/en/mydata/technical-specifications-versions-mydata |
| Source page (EL) | https://www.aade.gr/mydata/tehnikes-prodiagrafes-ekdoseis-mydata |

⚠️ **Do not use `https://www.aade.gr/mydata/prodiagrafes`.** It presents itself as the specifications
page and is stale — as of 2026-08-03 it still serves v0.6 from 2020. The versions page linked above is
the live one. This trap is recorded because it is the page a search engine offers first.

---

## Files

**Contents are unmodified, and so are the names.** *"Never edited"* extends to filenames: what is on
disk is byte-for-byte and name-for-name what AADE published. **Nothing here is renamed, and nothing
here should be.** A file whose name has been tidied can no longer be matched against AADE's own
download page, which is half of what makes it an artefact rather than a copy.

The table below is the **actual directory contents, verified 2026-08-03**. Quote these names exactly.

| File, as AADE published it | What it is | Used by |
|---|---|---|
| `myDATA API Documentation v2.0.1_official_erp.pdf` | REST API for ERP users, 122 pages. **Its §8 annex tables are the codifications** — see the annex map below | R1 (seeding), 29 |
| `myDATA API Documentation_Providers_v2 0 1_official.pdf` | REST API for certified electronic invoicing providers | 38 |
| `myDATA API Documentation_DeliveryNote_v2.0.1_official.pdf` | REST API for digital goods movement data. Carries the transport tables, including `transportType` | 18b |
| `syndiasmoi_xaraktirismwn_v2.0.1.xlsx` | Permitted income/expense classification combinations per document type | deferred validation |
| `v2.0.1 XSDs/` | The schema files, as shipped — unzipped. 21 files, listed below | R1, 29, the diff check |

The zip was unzipped deliberately: git can diff text XSDs and cannot diff an archive, and version-to-
version diffing is the whole reason these are kept. **The directory kept the archive's own name**,
`v2.0.1 XSDs` — spaces and all.

### ⚠️ A `*-v2.0.1.xsd` glob is NOT uniform. Do not write one

**`GenerateGroupQRCodeResponse -v2.0.1.xsd` carries a stray space before the dash.** That is AADE's
own filename and it is **preserved deliberately**, not overlooked. Twenty of the twenty-one files
match `*-v2.0.1.xsd`; that one does not match a pattern anchored on `Response-`, and any script that
assumes a consistent separator will silently process twenty files and report success.

Whoever scripts against this directory should **enumerate the files and assert the count is 21**
rather than glob and trust. The same caution applies to the PDFs: one of them spells the version
`v2 0 1` with spaces instead of dots.

### `v2.0.1 XSDs/` — the 21 files, verified 2026-08-03

| File | What it carries |
|---|---|
| `SimpleTypes-v2.0.1.xsd` | ⭐ **The authoritative machine-readable enumerations.** `InvoiceType` (55 values), `IncomeClassificationCategoryType` (12), `ExpensesClassificationCategoryType` (15), `IncomeClassificationValueType`, `ExpensesClassificationValueType`, `VatType`, `VatExemptionType`, `FeesType`, `WithheldType`, `OtherTaxesType`, `StampDutyType`, `DeductionsType`, `QuantityType`, `FuelCodes`, `SpecialInvoiceCategoryType`, `InvoiceVariationType`, `CountryType`, `CurrencyType`, `ReverseDeliveryNotePurposeType`, `DeliveryOutcomeType` |
| `InvoicesDoc-v2.0.1.xsd` | The invoice submission document |
| `InvoicesDoc-v2.0.1_aade_detailed.xsd` | The same, with AADE's detailed annotations |
| `requestedInvoicesDoc-v2.0.1.xsd` | The retrieval response shape |
| `incomeClassification-v2.0.1.xsd` | Income classification element definitions |
| `expensesClassification-v2.0.1.xsd` | Expense classification element definitions |
| `paymentMethods-v2.0.1.xsd` | Payment-method element definitions |
| `response-v2.0.1.xsd` | The transmission response, including where the ΜΑΡΚ and QR URL arrive |
| `RequestedStatementDoc-v2.0.1.xsd` | Statement retrieval |
| `SendStatement-v2.0.1.xsd` | Statement submission |
| `RequestE3InfoResponse-v2.0.1.xsd` | E3 figures retrieval |
| `RequestVatInfoResponse-v2.0.1.xsd` | VAT figures retrieval |
| `RequestedProviderDoc-v2.0.1.xsd` | Provider-side document retrieval (step 38) |
| `TransportTypes-v2.0.1.xsd` | Transport types (18b) |
| `RegisterTransfer-v2.0.1.xsd` | Goods-movement registration (18b) |
| `ConfirmDeliveryOutcome-v2.0.1.xsd` | Delivery outcome confirmation (18b) |
| `RejectDeliveryNote-v2.0.1.xsd` | Delivery-note rejection (18b) |
| `GetDeliveryStatusResponse-v2.0.1.xsd` | Delivery status (18b) |
| `GenerateGroupQRCode-v2.0.1.xsd` | Group QR code generation |
| `GenerateGroupQRCodeResponse -v2.0.1.xsd` | Its response. ⚠️ Note the space before the dash |
| `RequestGroupQRDetailsResponse-v2.0.1.xsd` | Group QR detail retrieval |

---

## Which annex table carries which codification

**§8 of `myDATA API Documentation v2.0.1_official_erp.pdf`.** Verified against the document itself on
2026-08-03, so a seed can cite a table number rather than assert a provenance.

| Annex | Title | Codification | Novocore use |
|---|---|---|---|
| **8.1** | Είδη παραστατικών | **Document types**, code → Greek description, grouped by issuer/recipient and by whether the document is αντικριζόμενο | ⭐ **R1 A**, both document-type tables |
| 8.2 | Κατηγορία Φ.Π.Α. | VAT category, codes 1–10 | Not seeded today — see the note below |
| **8.3** | Κατηγορία Αιτίας Εξαίρεσης ΦΠΑ | **VAT exemption reasons**, codes 1–31, with the ν.2859/2000 and ν.5144/2024 article numbering side by side | ⭐ Already seeded (`V8`) **from Prosvasis Go, not from here** — see the note below |
| 8.4 | Κατηγορία Παρακρατούμενων Φόρων | Withheld taxes | — |
| 8.5 | Κατηγορία Λοιπών Φόρων | Other taxes | — |
| 8.6 | Κατηγορία Συντελεστή Ψηφιακού Τέλους συναλλαγής | Digital transaction duty rates | — |
| **8.7** | Κατηγορία Τελών | **Statutory levies**, codes 1–22 — mobile telephony, subscription TV, plastic-bag and recycling levies, hotel-stay duty, restaurant/casino gross-receipts duties | ⚠️ **NOT delivery or COD fees.** See the note below |
| **8.8** | Κωδικός Κατηγορίας Χαρακτηρισμού Εσόδων | **Income classification category** — `category1_1`…`category1_10`, `category1_95`, `category3` | 📌 Fees, unscheduled |
| **8.9** | Κωδικός Τύπου Χαρακτηρισμού Εσόδων | **Income classification type** — the `E3_*` codes | 📌 Fees, unscheduled |
| **8.10** | Κωδικός Κατηγορίας Χαρακτηρισμού Εξόδων | **Expense classification category** — `category2_1`…`category2_14`, `category2_95` | 📌 Fees, unscheduled |
| **8.11** | Κωδικός Τύπου Χαρακτηρισμού Εξόδων | **Expense classification type** — the `E3_*` and `VAT_*` codes | 📌 Fees, unscheduled |
| **8.12** | Τρόποι Πληρωμής | **Payment methods**, codes 1–8 | ⭐ **R1 D** |
| **8.13** | Είδος Ποσότητας | **Quantity / unit-of-measure codes** | ⭐ **R1 F3.** `unit_of_measure.mydata_code` was all-NULL and recorded as *"pending the accountant"* (Q38) — **this table is the published list it was waiting for**, so the question was never the accountant's |
| 8.14 | Σκοπός Διακίνησης | Transport purposes | 18b — explicitly out of R1 |
| 8.15 | Επισήμανση | Line marking | — |
| 8.16 | Είδος Γραμμής | Line kind | — |
| 8.17 | Κωδικοί Καυσίμων | Fuel codes | Not applicable to this business |
| 8.18 | Τύπος Απόκλισης Παραστατικού | Document variation type | — |
| 8.19 | Ειδική Κατηγορία Παραστατικού | Special document category | — |
| 8.20 | Κατηγορία Οντότητας (EntityType) | Entity category | — |
| 8.21 | Αιτία Έκδοσης Αντίστροφης Διακίνησης | Reverse-movement reason | 18b |
| 8.22 | Κατάσταση Παραστατικού Δελτίου Διακίνησης | Delivery-note status | 18b |
| 8.23 | Τύποι Συσκευασίας (PackagingType) | Packaging type | 18b |

⚠️ **Read codes out of the XSD, descriptions out of the annex.** The PDF's tables are laid out in a
way that `pdftotext -layout` scrambles — in several tables the code column and the description column
drift apart by one or more rows, so a code/description pair read out of extracted text can be wrong
while looking entirely plausible. `SimpleTypes-v2.0.1.xsd` carries the same code sets as a flat
enumeration with no layout at all, and is the safe side of the pair. **A seed must take its code list
from the XSD and its Greek description from a human reading of the annex page**, never from a text
dump of the annex alone.

### Four things this folder settled that documents in the repository had recorded as open

1. **Exemption reason codes 24 and 28 exist.** `V8__vat_exemption_reason_seed.sql` seeds 29 rows with
   gaps at 24 and 28, and says in its own header that the two are *"absent from Go's list rather than
   known to be retired by AADE"*, asking for confirmation against AADE's published table. **Annex 8.3
   defines all 31 with no gaps**: 24 is `Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ` and 28 is
   `Χωρίς ΦΠΑ – άρθρο 29 περ. β' παρ.1 του Κώδικα ΦΠΑ, (Tax Free)`. So the seed is **26 of 31 codes
   plus 3 NULL-coded OSS/IOSS rows, and 2 codes it does not have at all.**
2. **The seeded descriptions match AADE's ν.5144/2024 column exactly** for every code Novocore has —
   so the article recodification `V8` transcribed from Go is confirmed against the authority.
3. **Annex 8.7 is not what "Fees" means.** It is a closed list of statutory levies — mobile
   telephony, plastic bags, subscription TV, hotel stays, restaurant and casino gross receipts. A
   delivery charge and a COD fee are ordinary revenue lines and appear nowhere in it. ⚠️ **This is
   why Fees was cut from R1** and left unscheduled; its AADE grounding is 8.8–8.11, not 8.7.
4. ⚠️ **The myDATA unit-of-measure codes were never an accountant question.** Q38 sat on the
   *"waiting on the accountant"* list from step 3b, beside the exemption codes and the depreciation
   rates, because `unit_of_measure.mydata_code` was NULL on every row and nobody had a source.
   **Annex 8.13 is that source and has been published all along.** The lesson is not about units:
   *a question was filed against a person because the artefact that answers it was not in the
   repository.* Three of the four items on this list are the same shape.

### What is *not* here

`vat_class` stores **Prosvasis Go's** rate code (`'1410'`, `'1030'`, …), not AADE's VAT category code
from annex 8.2. There is no myDATA code on a VAT class today, in either direction. Recorded so a
future reader does not mistake Go's code for an AADE one.

---

## What points at this folder

**The spec-version marker.** R1 stores a single value recording which specification version the seeded
codifications correspond to. That value points here. Without it, "are we behind AADE?" is a question
with no answer.

**The future diff check** (belongs with step 29, the myDATA adapter). Every prior version is archived on
the source page, so a new release can be diffed against this one file by file. The check **alerts a
human and never auto-applies** — a code list that updated itself would silently change what
already-transmitted documents claim.

---

## When AADE publishes a new version

Do not overwrite this folder. Create `docs/aade/v<new>/` beside it, with its own README, and leave this
one in place. The diff check needs both sides, and a document seeded under v2.0.1 was seeded under
v2.0.1 regardless of what is current.

Superseding the seed is a deliberate step with its own decision, not a consequence of downloading a file.

---

## Notes on v2.0.1 itself

Recorded because they bear on Novocore's own scheduling, not as a summary of the changelog:

- v2.0.1 is largely a **digital delivery note** release. Much of its changelog concerns dispatch rather
  than invoicing, which is why the delivery-note document above matters to 18b and not to R1.
- It renamed `invoiveDeliveryStatus` to `invoiceDeliveryStatus`, and changed `transportType`'s value
  range to 1–7 with a new explanatory annex table. **These lists move.** That is the evidence behind the
  spec-version marker and the diff check, and the reason neither is optional.
- Fuel document types 9.1, 9.2, 10.1 and 10.2 were enriched. Not applicable to this business today;
  noted so a future reader does not mistake their absence from the seed for an omission.

---

## Seeding rule

Every AADE code in Novocore comes from these artefacts. **Nothing is invented, guessed or inferred.**
Where a value cannot be sourced with confidence it is seeded `null` and listed as open — the same
treatment already used for the OSS/IOSS exemption codes and the myDATA unit-of-measure codes.
