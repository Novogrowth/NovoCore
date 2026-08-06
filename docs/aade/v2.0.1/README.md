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
| **8.1** | Είδη παραστατικών | **Document types**, code → Greek description, grouped by issuer/recipient and by whether the document is αντικριζόμενο | ⭐ **Seeded by R1a into `aade_invoice_type`** — all 55, group as a column. ⚠️ Codes **`4` and `12` have an EMPTY description cell**; their only label is the group heading `Για Μελλοντική Χρήση` |
| 8.2 | Κατηγορία Φ.Π.Α. | VAT category, codes 1–10 | Not seeded today — see the note below |
| **8.3** | Κατηγορία Αιτίας Εξαίρεσης ΦΠΑ | **VAT exemption reasons**, codes 1–31, with the ν.2859/2000 and ν.5144/2024 article numbering side by side | ⭐ **Complete since R1a.** `V8` seeded 26 + 3 from Prosvasis Go; `V32` added **24 and 28** from here. ⚠️ **This annex contains NO myDATA wire strings** — see the note below |
| 8.4 | Κατηγορία Παρακρατούμενων Φόρων | Withheld taxes | — |
| 8.5 | Κατηγορία Λοιπών Φόρων | Other taxes | — |
| 8.6 | Κατηγορία Συντελεστή Ψηφιακού Τέλους συναλλαγής | Digital transaction duty rates | — |
| **8.7** | Κατηγορία Τελών | **Statutory levies**, codes 1–22 — mobile telephony, subscription TV, plastic-bag and recycling levies, hotel-stay duty, restaurant/casino gross-receipts duties | ⚠️ **NOT delivery or COD fees.** See the note below |
| **8.8** | Κωδικός Κατηγορίας Χαρακτηρισμού Εσόδων | **Income classification category** — `category1_1`…`category1_10`, `category1_95`, `category3` | 📌 Fees, unscheduled |
| **8.9** | Κωδικός Τύπου Χαρακτηρισμού Εσόδων | **Income classification type** — the `E3_*` codes | 📌 Fees, unscheduled |
| **8.10** | Κωδικός Κατηγορίας Χαρακτηρισμού Εξόδων | **Expense classification category** — `category2_1`…`category2_14`, `category2_95` | 📌 Fees, unscheduled |
| **8.11** | Κωδικός Τύπου Χαρακτηρισμού Εξόδων | **Expense classification type** — the `E3_*` and `VAT_*` codes | 📌 Fees, unscheduled |
| **8.12** | Τρόποι Πληρωμής | **Payment methods**, codes 1–8 | ⭐ **R1 D**, and ⚠️ **THE ONE ANNEX WHOSE CODES THE XSD DOES NOT CARRY — read note 5 before seeding it.** Read from a rasterised page 2026-08-06 (R4 G.1): **1** Επαγ. Λογαριασμός Πληρωμών Ημεδαπής · **2** … Αλλοδαπής · **3 Μετρητά** · **4** Επιταγή · **5** Επί Πιστώσει · **6** Web Banking · **7** POS / e-POS · **8** Άμεσες Πληρωμές IRIS |
| **8.13** | Είδος Ποσότητας | **Quantity / unit-of-measure codes** — ⚠️ **SEVEN**, `1..7` per `QuantityType` | ⭐ **R1a.** Four of NovoCore's eight units mapped; **four left NULL and open** — two have no AADE code at all, two are a real judgement. See note 4 below |
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

#### ⚠️ 5. …AND FOR ANNEX 8.12 THE SAFE SIDE OF THAT PAIR DOES NOT EXIST. Found 2026-08-06 (R4 G.1)

**The rule above has a hole, and it is exactly one annex wide.** `paymentMethods-v2.0.1.xsd` defines
no code list at all; the type is in `InvoicesDoc-v2.0.1.xsd`, and it is

```xml
<xs:element name="type">          <!-- Τύπος Πληρωμής -->
  <xs:simpleType><xs:restriction base="xs:int">
    <xs:minInclusive value="1"/><xs:maxInclusive value="8"/>
  </xs:restriction></xs:simpleType>
</xs:element>
```

**A RANGE, not an enumeration.** It says how many codes there are and nothing whatever about what any
of them means. So *"take the code list from the XSD"* is **unavailable here**: there is no flat
enumeration to take, and the codes and descriptions can only come from **the same artefact — annex
8.12** — which is the single-source condition the rule exists to forbid.

⚠️ **This matters because the rule is written in a way that reads as universal**, and a session that
follows it will look for an enumeration, not find one, and be tempted either to give up or to fall
back on `pdftotext`. **Neither is right. The answer is to rasterise the page and read it**, which is
what the rule's second half already prescribes for descriptions — here it has to carry the codes too.

**Done on 2026-08-06**: PDF page **105** (document page 104) rendered at 200 dpi via PyMuPDF and read
by eye. The eight pairs are in the annex table above. ⭐ **They match `SettlementMethod`'s javadoc
exactly**, which recorded the same eight from an independent rasterised read in R1a — **two readings
of the artefact agreeing, which is the closest thing to two sources available for this annex.**

📌 **Tooling note, because the obvious commands are absent on this machine:** there is no
`pdftoppm`, no ImageMagick, and the Read tool's PDF rendering depends on poppler and therefore fails.
**PyMuPDF (`import fitz`) is installed and works** — `doc[104].get_pixmap(dpi=200).save(...)`, then
read the PNG. Locating the page by text search is fine; it is only the *pairing* that a text dump may
not supply.

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
4. ⚠️ **The myDATA unit-of-measure codes were only PARTLY an accountant question.** Q38 sat on the
   *"waiting on the accountant"* list from step 3b, beside the exemption codes and the depreciation
   rates, because `unit_of_measure.mydata_code` was NULL on every row and nobody had a source.
   **Annex 8.13 is that source and has been published all along.**

   ⚠️ **Corrected by R1a, 2026-08-03, and the correction is the useful part.** The annex has
   **SEVEN** codes — confirmed against `QuantityType` in `SimpleTypes-v2.0.1.xsd`, which is
   `xs:int` restricted to `1..7` — and NovoCore has **eight** unit rows. They do not line up. Four
   map with certainty (`PIECE`→1, `KILOGRAM`→2, `LITRE`→3, `METRE`→4). **Four do not, for two
   different reasons:** `GRAM` and `MILLILITRE` have **no AADE code at all**, because the list has
   no sub-units and mapping a gram to `2 Κιλά` would transmit a quantity wrong by a factor of a
   thousand; `SET` and `PACK` are a genuine judgement between `1 Τεμάχια` and
   `7 Τεμάχια_Λοιπές Περιπτώσεις`, and the choice changes what is transmitted.

   **So the lesson is sharper than "it was never the accountant's question".** *The whole question
   was filed against a person because the artefact that answers most of it was not in the
   repository* — which left nobody able to see which part was actually theirs. Three of the four
   items on this list are the same shape.

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

### ⚠️ Annex 8.3 gives reason TEXT, not a myDATA wire string

**Established by R1a, 2026-08-03, and it changed what was seeded.** The approved scope said to seed
codes 24 and 28 *"with verbatim wire strings from annex 8.3"*. **The annex has no such column.** It
gives the exemption reason under two article numberings and nothing else.

The `N-description` form that `vat_exemption_reason.mydata_code` carries on 26 rows is **Prosvasis
Go's** rendering, transcribed verbatim by `V8` *precisely because composing one is a bet* — and
codes **12 and 13 are the standing proof that the bet loses**, their description naming
`Πλοία Ανοικτής Θαλάσσης` where their myDATA string does not.

**Go has no row for 24 or 28**, so there was nothing verbatim to copy. Both are seeded with
`mydata_code = NULL` and listed open, which is the stance already taken for OSS/IOSS: *no mapping
exists*, not *not filled in yet*.

⚠️ **Worth knowing before the myDATA adapter:** the wire type for this field is
`VatExemptionType`, an **`xs:int`**. The string in that column is a UI rendering, not the wire value.

## Seeding rule

Every AADE code in Novocore comes from these artefacts. **Nothing is invented, guessed or inferred.**
Where a value cannot be sourced with confidence it is seeded `null` and listed as open — the same
treatment already used for the OSS/IOSS exemption codes and the myDATA unit-of-measure codes.
