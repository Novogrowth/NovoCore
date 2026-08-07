# Prosvasis Go API Documentation 1.0

> Reference for the Prosvasis Go (SoftOne) REST API.
> Converted from the official HTML documentation export; content unchanged apart from formatting.

---

## ⚠️ PROVENANCE AND EVIDENCE CLASS — read before using anything below

**Committed 2026-08-07 by U5**, discharging roadmap obligation **ᵍᵒ / N-5**. The vendor's
documentation is a JavaScript-rendered page a session cannot read; this text extraction exists so
nobody has to re-obtain it.

| | |
|---|---|
| **Vendor documentation version** | **1.0**, as stated in the title above. **No finer version string appears anywhere in the source** |
| **Date added to this repository** | **2026-08-07** |
| ✅ **Date the vendor's HTML was captured** | **2026-08-07 — CONFIRMED BY THE OWNER (U6, 2026-08-07).** This row previously read *"NOT KNOWN"* and was recorded as a real gap; **it is closed.** Capture and commit are the same day, so this extraction is not a stale copy of an older page |
| ✅ **URL the vendor's HTML was captured FROM** — **AUTHORITATIVE** | **`https://s1sites.s1cloud.net/s1docs/goapi/docs/index.html`** — supplied by the owner, **2026-08-07**. **This is the ARTEFACT this file was converted from.** ⚠️ It is neither the API base nor a page a fetcher can read — see below |
| ⭐ **Where a human FINDS that artefact** — the durable entry point | **`https://wiki.prosvasis.com/display/GO/ProsvasisGO+API+documentation`**. The vendor's own wiki page, which **links to the s1sites URL above.** ⭐ **Start here if the artefact URL has moved** |
| **Evidence class** | ⚠️ **DOCUMENTED, NOT OBSERVED.** Nothing here has been exercised against a live Go system from this repository, and under `CLAUDE.md` non-negotiable **rule 9** no session may do so without the owner's explicit instruction |

### ⚠️ N-8 — THIS FILE IS A CONVERTED COPY, AND IT WILL GO STALE SILENTLY

**Recorded 2026-08-07 (U6).** The line under the title already says the content is converted; **this
says what follows from that**, which is the part a reader acts on.

#### ⚠️ TWO URLs, and the distinction between them is the point rather than a formality

**They are not alternatives and neither replaces the other:**

- **`s1sites.s1cloud.net/s1docs/goapi/docs/index.html` is the ARTEFACT** — the exact page whose text
  is below. It is what makes this file checkable against its source. ⚠️ **It is also the fragile one:**
  a docs-hosting path is the kind of URL a vendor reorganises without notice.
- ⭐ **`wiki.prosvasis.com/display/GO/ProsvasisGO+API+documentation` is the ENTRY POINT** — the
  vendor's own wiki page, which links to the artefact. **It is what a human uses to find the artefact
  again when the artefact URL has moved.**

📌 **Recording only the first would leave a dead link and no route back; recording only the second
would leave nobody able to say what this file was converted from.**

⚠️ **EVIDENCE CLASS: both URLs were read on 2026-08-07 by the owner's side of the conversation, not
from inside this repository.** No session has fetched either — rule 9 governs outbound requests, and
in any case the artefact cannot be fetched usefully, which is the next paragraph.

##### ⭐ AND THIS IS WHY THE COPY IS JUSTIFIED, not merely convenient

**The s1sites page is JavaScript-rendered and returns NO READABLE CONTENT to a fetcher.** A session
that follows the URL gets a shell, not the documentation. ⭐ **That is the whole reason this file
exists** — the alternative is not *"read the vendor's page instead"*, it is *"nobody can read the
vendor's page from here at all."*

📌 **It is also why the staleness below cannot be automated away.** A diff check needs something to
diff against, and a fetcher cannot obtain one. **A human with a browser is the only mechanism.**

**This document is a text extraction of a JavaScript-rendered vendor page. It is not the source.**
The vendor's page is authoritative and this is a copy of it taken at one moment. ⚠️ **When the vendor
changes their documentation, nothing here changes and nothing anywhere reports it** — there is no
fetch, no diff, no check, and there cannot be one while the source is a rendered page and rule 9
governs outbound requests. **The failure mode is silence**, which is why it is written at the top
rather than assumed.

📌 **The practical consequence, stated so it is usable:** a disagreement between this file and
observed Go behaviour is **evidence about this file's age**, not necessarily a bug in either. Check
the capture date above before concluding the API changed.

⚠️ **Same shape as the AADE codification artefacts** (`docs/aade/v2.0.1/`) — a versioned copy of
somebody else's document, held locally because it cannot be fetched on demand, needing a human diff
rather than an automatic one. `CLAUDE.md`'s standing rule for those applies here: **alert a human,
never auto-apply.**

⚠️ **DOCUMENTED IS NOT VERIFIED, and the gap is what roadmap row G1 exists to close.** Two working
integrations already exist outside this codebase; **their behavioural findings — what a retry does,
what errors look like, whether stock is reachable at all — are what an adapter is mostly made of, and
none of it is here.** Read G1 before scoping step 18.

📌 **Three things this document settles, recorded at the roadmap rather than only here:** Go offers
**no idempotency mechanism** (`set/saldoc` with an empty `key` inserts), **issuance is two calls**
(`set/saldoc` then `/s1services/einvoice`), and the einvoice response carries **two nested `success`
flags — outer `true` with inner `false` means Go transmitted and AADE REJECTED.**

📌 **A note on the repeated `Example: 10502454783619`.** It appears **36 times, identically, on every
endpoint** — the shape of vendor template boilerplate rather than a real credential, which is why it
was left intact rather than redacted. ✅ **CLOSED 2026-08-07 (U6): the owner confirms it is the
VENDOR'S DOCUMENTATION EXAMPLE and not his own `s1code`.** U5 left this as a conditional — *"confirm
it is the vendor's example… before this repository is ever made public"* — and the condition is now
answered. **No redaction is owed.**

---

## Conventions

> ### ⚠️ THIS SECTION IS EDITORIAL. It is NOT vendor text
>
> **Everything from here to the endpoint index was WRITTEN BY THIS REPOSITORY**, summarising the
> per-endpoint bodies below. Every other section of this file is the vendor's own words.
>
> ⚠️ **So this is the one part of the document that can be WRONG WITHOUT THE VENDOR BEING WRONG.** A
> summary is a second record of what the body says, and this repository has paid repeatedly for two
> records of one thing drifting apart. **When a bullet here disagrees with an endpoint section below,
> the endpoint section wins** — it is the source, this is the digest.
>
> 📌 **The `LINENUM` bullet is the live example and the reason this marker exists.** It states the
> child-table deletion rule as though it were general. **The vendor states it on exactly two
> endpoints** — `set/customer` and `set/supplier` — and **nowhere else**. See **N-6** at roadmap ᵍᵒ:
> the generalisation is this repository's inference, not the vendor's claim, and it matters because
> `set/saldoc` also has child tables.

- **Base URL:** `https://go.s1cloud.net/`
- **All endpoints are `POST`**, even the `get`/`list`/`del` ones — the verb is part of the path, not the HTTP method.
- **Authentication** is passed per request, not via a bearer token:
  - `s1code` **request header** — the *Username* provided to you (e.g. `10502454783619`)
  - `appId` **in the body** — the appId provided to you
  - `token` **in the body** — the *Password* provided to you
- **Response encoding:** `application/json; charset=windows-1253` — responses are **not** UTF-8. Decode as Windows-1253 (Greek) before parsing, or Greek text will be mangled.
- **`locateinfo`** selects which entities/fields come back. Format: `ENTITY:FIELD1,FIELD2;ENTITY2:FIELD1,...` (`;` separates entities).
- **`filters`** on `list/*` endpoints: `ENTITY.FIELD=value & ENTITY.FIELD_TO=value` (`&` separates filters). Empty string = no filtering.
- **`key`** identifies an existing record. On `set/*` endpoints, leave `key` blank to insert a new record; supply it to update.
- **`LINENUM`** (detail/child tables): use numbers from `9000001` upward for new lines. When updating a record whose child table already has rows, you must re-send the `LINENUM` of every existing row you want to keep (LINENUM alone is enough) — omitted rows are deleted.
  - ⚠️ **SCOPE: the vendor states this on `set/customer` and `set/supplier` ONLY.** Whether `set/saldoc`'s child tables behave the same way is **UNDOCUMENTED** — see **N-6** at roadmap ᵍᵒ. **This bullet's phrasing is editorial and generalises; the vendor did not.**
- Schema blocks below use the source's pseudo-JSON notation (`field: type`), not literal JSON.

## Endpoint index

| Resource | Operation | Path |
| --- | --- | --- |
| Customer | [Delete Customer](#delete-customer) | `/s1services/del/customer` |
| Customer | [Get Customer](#get-customer) | `/s1services/get/customer` |
| Customer | [List all Customers](#list-all-customers) | `/s1services/list/customer` |
| Customer | [Insert or Update Customer](#insert-or-update-customer) | `/s1services/set/customer` |
| Supplier | [Delete Supplier](#delete-supplier) | `/s1services/del/supplier` |
| Supplier | [Get Supplier](#get-supplier) | `/s1services/get/supplier` |
| Supplier | [List all Suppliers](#list-all-suppliers) | `/s1services/list/supplier` |
| Supplier | [Insert or Update Supplier](#insert-or-update-supplier) | `/s1services/set/supplier` |
| Item | [Delete Item](#delete-item) | `/s1services/del/item` |
| Item | [Get Item](#get-item) | `/s1services/get/item` |
| Item | [List all Items](#list-all-items) | `/s1services/list/item` |
| Item | [Insert or Update Item](#insert-or-update-item) | `/s1services/set/item` |
| Service | [Delete Service](#delete-service) | `/s1services/del/service` |
| Service | [Get Service](#get-service) | `/s1services/get/service` |
| Service | [List all Services](#list-all-services) | `/s1services/list/service` |
| Service | [Insert or Update Service](#insert-or-update-service) | `/s1services/set/service` |
| Sales Documents | [Delete Sales Document](#delete-sales-document) | `/s1services/del/saldoc` |
| Sales Documents | [Get Sales Document](#get-sales-document) | `/s1services/get/saldoc` |
| Sales Documents | [List all Sales Documents](#list-all-sales-documents) | `/s1services/list/saldoc` |
| Sales Documents | [Insert or Update Sales Document](#insert-or-update-sales-document) | `/s1services/set/saldoc` |
| Einvoice | [Get Einvoice signature](#get-einvoice-signature) | `/s1services/einvoice` |
| Purchases Documents | [Delete Purchases Document](#delete-purchases-document) | `/s1services/del/purdoc` |
| Purchases Documents | [Get Purchases Document](#get-purchases-document) | `/s1services/get/purdoc` |
| Purchases Documents | [List all Purchases Documents](#list-all-purchases-documents) | `/s1services/list/purdoc` |
| Purchases Documents | [Insert or Update Purchases Document](#insert-or-update-purchases-document) | `/s1services/set/purdoc` |
| Receipts Documents | [Delete Receipts Document](#delete-receipts-document) | `/s1services/del/cfncusdoc` |
| Receipts Documents | [Get Receipts Document](#get-receipts-document) | `/s1services/get/cfncusdoc` |
| Receipts Documents | [List all Receipts Documents](#list-all-receipts-documents) | `/s1services/list/cfncusdoc` |
| Receipts Documents | [Insert or Update Receipts Document](#insert-or-update-receipts-document) | `/s1services/set/cfncusdoc` |
| Payments Documents | [Delete Payments Document](#delete-payments-document) | `/s1services/del/cfnsupdoc` |
| Payments Documents | [Get Payments Document](#get-payments-document) | `/s1services/get/cfnsupdoc` |
| Payments Documents | [List all Payments Documents](#list-all-payments-documents) | `/s1services/list/cfnsupdoc` |
| Payments Documents | [Insert or Update Payments Document](#insert-or-update-payments-document) | `/s1services/set/cfnsupdoc` |
| Webhook | [Create or Modify a Webhook](#create-or-modify-a-webhook) | `/s1services/webhook/create` |
| Webhook | [Delete an existing Webhook](#delete-an-existing-webhook) | `/s1services/webhook/del` |

---

# Customer

## Delete Customer

```
POST /s1services/del/customer
```

Use this endpoint in order to delete Customer.

### Body contents

- In appId use the appId that was provided to you .
- In key use key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json` (required)

```
{
  appId: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Customer deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Customer

```
POST /s1services/get/customer
```

Use this endpoint in order to get Customer.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entity names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - CUSBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS (Customer's bank account information)
  - CUSTOMER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP (Customer's general information)
  - If you want multiple data from many entities you can use the delimeter ; .Use all or some of them depends on what you want.
- An example could be:
  - locateinfo:"CUSBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS;CUSTOMER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP"
- In key use key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json` (required)

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Customer founds (true) otherwise (false)
- data is the object with the arrays that you request. For example CUSTOMER, CUSBANKACC
- caption is the code and the name of Customer delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    CUSTOMER : [{
      ADDRESS: string
      AFM: string
      CITY: string
      CODE: string
      COUNTRY: string
      DISCOUNT: float
      DISTRICT: string
      EMAIL: string
      IRSDATA: string
      ISACTIVE: string
      JOBTYPETRD: string
      NAME: string
      PAYMENT: string
      PHONE01: string
      PHONE02: string
      TRDCATEGORY: string
      TRDR: string
      VATPROVISIONS: string
      VATSTS: string
      ZIP: string
    }]
    CUSBANKACC : [{
      BANK: string
      BANK_BANK_CODE: string
      BANK_BANK_NAME: string
      BANKACNNUM: string
      LINENUM: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Customers

```
POST /s1services/list/customer
```

Use this endpoint in order to list all Customers.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - CUSTOMER.CODE, CUSTOMER.CODE_TO
  - CUSTOMER.AFM, CUSTOMER.AFM_TO
  - CUSTOMER.NAME, CUSTOMER.NAME_TO
  - CUSTOMER.TRDR_CUSFINDATA_LBAL, CUSTOMER.TRDR_CUSFINDATA_LBAL_TO (Customer Balance)
  - CUSTOMER.ISACTIVE
- An example could be:
  - filters:'CUSTOMER.CODE=001 & CUSTOMER.CODE_TO=150 & CUSTOMER.AFM=165665589 & CUSTOMER.AFM_TO=165665590 & CUSTOMER.NAME=Dimitra & CUSTOMER.NAME_TO=Maria & CUSTOMER.TRDR_CUSFINDATA_LBAL=150 & CUSTOMER.TRDR_CUSFINDATA_LBAL_TO=2500 & CUSTOMER.ISACTIVE=1'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json` (required)

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if found Customers (true) otherwise (false)
- totalCount is property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each Customer

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Customer

```
POST /s1services/set/customer
```

Use this endpoint in order to insert or update a Customer.

### Body contents

- In data use the entities that you want to insert.
- The entities and the fields that you can use are the following:
  - CUSTOMER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP
  - CUSBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS
  - Notice on LINENUM:
  - LINENUM: In order to add or update data to child (detail) tables you also need to define the field LINENUM. For adding new records use numbers from 9000001 and up. Notice that if the child table contains records already, and you need to update or add new, you have to insert the LINENUM field number of the existing ones (without using any other fields), or else those records will be deleted.
- In appId use the appId that was provided to you .
- In locateinfo use entity names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - CUSBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS (Customer's bank account information)
  - CUSTOMER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP (Customer's general information)
  - If you want multiple data from many entities you can use the delimeter ; .Use all or some of them depends on what you want.
- An example could be:
  - locateinfo:"CUSBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS;CUSTOMER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP"
- In key use key of the record that you need to modify, leave it blank if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  data : {
    CUSBANKACC : [{
      BANK: integer
      IBAN: string
      LINENUM: integer
      BANKACNNUM: string
      REMARKS: string
    }]
    CUSTOMER : [{
      ZIP: string
      PAYMENT: integer
      PHONE01: string
      TRDCATEGORY: integer
      VATPROVISIONS: integer
      GASCUSTYPE: integer
      AFM: string
      REMARKS: string
      EMAIL: string
      ISACTIVE: boolean
      NAME: string
      DISCOUNT: float
      DISTRICT: string
      IRSDATA: string
      CODE: string
      CITY: string
      COUNTRY: integer
      VATSTS: integer
      JOBTYPETRD: string
      ADDRESS: string
      FAX: string
      PHONE02: string
    }]
  }
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Customer inserted or updated (true) otherwise (false)
- id the id of Customer
- data is the object with the entities that you requested
- caption is the code and the name of Customer delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    CUSTOMER : [{
      ADDRESS: string
      AFM: string
      CITY: string
      CODE: string
      COUNTRY: string
      DISCOUNT: string
      DISTRICT: string
      EMAIL: string
      FAX: string
      IRSDATA: string
      ISACTIVE: string
      JOBTYPETRD: string
      NAME: string
      PAYMENT: string
      PHONE01: string
      PHONE02: string
      REMARKS: string
      TRDCATEGORY: string
      TRDR: string
      VATPROVISIONS: string
      VATSTS: string
      ZIP: string
    }]
    CUSBANKACC : [{
      BANK: string
      BANK_BANK_CODE: string
      BANK_BANK_NAME: string
      BANKACNNUM: string
      IBAN: string
      LINENUM: string
      REMARKS: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

# Supplier

## Delete Supplier

```
POST /s1services/del/supplier
```

Use this endpoint in order to delete Supplier.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: integer
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Customer deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Supplier

```
POST /s1services/get/supplier
```

Use this endpoint in order to get Supplier.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - SUPBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS (Supplier's bank account information)
  - SUPPLIER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP (Supplier's general information)
  - If you want multiple data from many entities you can use the delimeter ;
- An example could be:
  - locateinfo:"SUPBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS;SUPPLLIER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json; charset=windows-1253`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if Supplier found false otherwise
- data is the object with the entities that you request for example SUPPLIER, SUPBANKAC
- caption is the code and the name of Supplier delimetered with -

`application/json; charset=windows-1253`

```
{
  SUPPLIER : [{
    ADDRESS: string
    AFM: string
    CITY: string
    CODE: string
    COUNTRY: string
    DISCOUNT: string
    DISTRICT: string
    EMAIL: string
    IRSDATA: string
    ISACTIVE: string
    JOBTYPETRD: string
    NAME: string
    PAYMENT: string
    PHONE01: string
    PHONE02: string
    TRDCATEGORY: string
    TRDR: string
    VATPROVISIONS: string
    VATSTS: string
    ZIP: string
  }]
  SUPBANKACC : [{
    BANK: string
    BANK_BANK_CODE: string
    BANK_BANK_NAME: string
    BANKACNNUM: string
    LINENUM: string
  }]
  XTRDOCDATA : [{
    LINENUM: string
    SOFNAME: string
    NAME: string
  }]
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Suppliers

```
POST /s1services/list/supplier
```

Use this endpoint in order to list all Suppliers.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - SUPPLIER.CODE, SUPPLIER.CODE_TO
  - SUPPLIER.AFM, SUPPLIER.AFM_TO
  - SUPPLIER.NAME, SUPPLIER.NAME_TO
  - SUPPLIER.TRDR_CUSFINDATA_LBAL, SUPPLIER.TRDR_CUSFINDATA_LBAL_TO (Supplier's Balance)
  - SUPPLIER.ISACTIVE
- An example could be:
  - filters:'SUPPLIER.CODE=001 & SUPPLIER.CODE_TO=150 & SUPPLIER.AFM=165665589 & SUPPLIER.AFM_TO=165665590 & SUPPLIER.NAME=Dimitra & SUPPLIER.NAME_TO=Maria & SUPPLIER.TRDR_CUSFINDATA_LBAL=150 & SUPPLIER.TRDR_CUSFINDATA_LBAL_TO=2500 & SUPPLIER.ISACTIVE=1'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json` (required)

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are Suppliers, false otherwise
- totalCount is property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each Supplier

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Supplier

```
POST /s1services/set/supplier
```

Use this endpoint in order to insert or update a Supplier.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - SUPPLIER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP
  - SUPBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS
  - Notice on LINENUM:
  - LINENUM: In order to add or update data to child (detail) tables you also need to define the field LINENUM. For adding new records use numbers from 9000001 and up. Notice that if the child table contains records already, and you need to update or add new, you have to insert the LINENUM field number of the existing ones (without using any other fields), or else those records will be deleted
- In appId use the appId that was provided to you .
- In locateinfo Use entity names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you have access are the following:
  - SUPBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS (Customer's bank account information)
  - SUPPLIER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP
  - If you want fields of many entities use as delimeter the ; .Use all or some of them depending on what you want.
- An example could be:
  - locateinfo:"SUPBANKACC:BANK,BANK_BANK_CODE,BANK_BANK_NAME,BANKACNNUM,IBAN,LINENUM,REMARKS;SUPPLIER:ADDRESS,AFM,CITY,CODE,COUNTRY,DISCOUNT,DISTRICT,EMAIL,FAX,GASCUSTYPE,IRSDATA,ISACTIVE,JOBTYPETRD,NAME,PAYMENT,PHONE01,PHONE02,REMARKS,TRDCATEGORY,TRDR,VATPROVISIONS,VATSTS,ZIP"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  data : {
    SUPBANKACC : [{
      BANK: integer
      IBAN: string
      LINENUM: integer
      BANKACNNUM: string
      REMARKS: string
    }]
    SUPPLIER : [{
      ZIP: string
      PAYMENT: integer
      PHONE01: string
      TRDCATEGORY: integer
      VATPROVISIONS: integer
      AFM: string
      REMARKS: string
      EMAIL: string
      ISACTIVE: string
      NAME: string
      DISTRICT: string
      IRSDATA: string
      CODE: string
      COUNTRY: integer
      CITY: string
      VATSTS: integer
      JOBTYPETRD: string
      ADDRESS: string
      FAX: string
      PHONE02: string
    }]
  }
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Supplier inserted or updated (true) otherwise (false)
- id the id of Supplier
- data is te object with the arrays that you request in locateinfo
- caption is the code and the name of Supplier delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    SUPPLIER : [{
      AFM: string
      CODE: string
      COUNTRY: string
      ISACTIVE: string
      NAME: string
      TRDCATEGORY: string
      TRDR: string
      VATSTS: string
    }]
    SUPBANKACC : [{
      BANK: string
      BANK_BANK_CODE: string
      BANK_BANK_NAME: string
      BANKACNNUM: string
      IBAN: string
      LINENUM: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
  message: string
}
```

---

# Item

## Delete Item

```
POST /s1services/del/item
```

Use this endpoint in order to in order to delete Item.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: integer
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Item deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Item

```
POST /s1services/get/item
```

Use this endpoint in order to get Item.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - ITEDOCDATA:SODATA,SOFNAME;
  - ITEM:CODE,CODE1,CRDCARDMODE,EXPN1,EXPN2,EXPN3,EXPVAL1,EXPVAL2,EXPVAL3,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRTYPE,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,SODISCOUNT,VAT
  - If you want multiple data from many entities you can use the delimeter ;
- An example could be:
  - locateinfo:"ITEDOCDATA:SODATA,SOFNAME;ITEM:CODE,CODE1,CRDCARDMODE,EXPN1,EXPN2,EXPN3,EXPVAL1,EXPVAL2,EXPVAL3,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRTYPE,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,SODISCOUNT,VAT"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if Items found false otherwise
- data is the object with the entities that you request for example ITEDOCDATA, ITEM
- caption is the code and the name of Item delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    ITEM : [{
      CODE: string
      CODE1: string
      CRDCARDMODE: string
      EXPVAL1: float
      EXPVAL2: float
      EXPVAL3: float
      ISACTIVE: boolean
      MTRCATEGORY: string
      MTRGROUP: string
      MTRL: string
      MTRTYPE: string
      MTRUNIT1: string
      NAME: string
      PRICER: float
      PRICEW: float
      SODISCOUNT: float
      VAT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Items

```
POST /s1services/list/item
```

Use this endpoint in order to list all Items.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - ITEM.CODE, ITEM.CODE_TO
  - ITEM.NAME, ITEM.NAME_TO
  - ITEM.MTRGROUP
  - ITEM.MTRCATEGORY
  - ITEM.ISACTIVE
- An example could be:
  - filters:'ITEM.CODE=001 & ITEM.CODE_TO=150 & ITEM.NAME=iph & ITEM.NAME_TO=tv & ITEM.MTRGROUP=1 & ITEM.MTRCATEGORY=1 & ITEM.ISACTIVE=1'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are Items, false otherwise
- totalCount is property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each Item

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Item

```
POST /s1services/set/item
```

Use this endpoint in order to in order to insert or update a Item.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - ITEDOCDATA:SODATA,SOFNAME;
  - ITEM:CODE,CODE1,CRDCARDMODE,EXPN1,EXPN2,EXPN3,EXPVAL1,EXPVAL2,EXPVAL3,ISACTIVE,MTRCATEGORY,MTRL,MTRTYPE,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,SODISCOUNT,VAT
- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - ITEDOCDATA:SODATA,SOFNAME;
  - ITEM:CODE,CODE1,CRDCARDMODE,EXPN1,EXPN2,EXPN3,EXPVAL1,EXPVAL2,EXPVAL3,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRTYPE,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,SODISCOUNT,VAT
  - If you want multiple data from many entities you can use the delimeter ;
- An example could be:
  - locateinfo:"ITEDOCDATA:SODATA,SOFNAME;ITEM:CODE,CODE1,CRDCARDMODE,EXPN1,EXPN2,EXPN3,EXPVAL1,EXPVAL2,EXPVAL3,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRTYPE,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,SODISCOUNT,VAT"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  data : {
    ITEM : [{
      PRICER: integer
      SODISCOUNT: integer
      MTRTYPE: integer
      MTRUNIT1: integer
      PRICEW: integer
      EXPVAL3: integer
      VAT: integer
      EXPVAL2: integer
      CRDCARDMODE: integer
      EXPVAL1: integer
      REMARKS: string
      ISACTIVE: string
      NAME: string
      CODE1: string
      CODE: string
      EXPN2: integer
      EXPN1: integer
      MTRGROUP: integer
      EXPN3: integer
      MTRCATEGORY: integer
    }]
    ITEDOCDATA : [{
      SOFNAME: string
      SODATA: string
    }]
    appId: string
    locateinfo: string
    key: string
    token: string
  }
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the Item inserted or updated (true) otherwise (false)
- id the id of Item
- data is te object with the arrays that you request in locateinfo
- caption is the code and the name of Item delimetered with -

`application/json; charset=windows-1253`

```
{
  data : {
    ITEM : [{
      CODE: string
      CRDCARDMODE: float
      EXPVAL1: float
      EXPVAL2: float
      EXPVAL3: float
      ISACTIVE: boolean
      MTRCATEGORY: int32
      MTRGROUP: int32
      MTRTYPE: int32
      MTRUNIT1: int32
      PRICER: float
      PRICEW: float
      SODISCOUNT: float
      VAT: int32
      NAME: string
      CODE1: string
      EXPN1: float
      EXPN2: float
      EXPN3: float
      REMARKS: string
    }]
    ITEDOCDATA : [{
      SOFNAME: string
      SODATA: string
    }]
  }
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

---

# Service

## Delete Service

```
POST /s1services/del/service
```

Use this endpoint in order to delete Service.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: integer
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

OK

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Service

```
POST /s1services/get/service
```

Use this endpoint in order to get Service.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - SERVICE:CODE,CRDCARDMODE,EXPN1,EXPVAL1,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,VAT
- An example could be:
  - locateinfo:"SERVICE:CODE,CRDCARDMODE,EXPN1,EXPVAL1,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,VAT"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if service found false otherwise
- data is the object with the entities that you request for example SERVICE
- caption is the code and the name of Service delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    SERVICE : [{
      CODE: string
      CRDCARDMODE: string
      EXPN1: float
      EXPVAL1: float
      EXPVAL2: float
      EXPVAL3: float
      ISACTIVE: string
      MTRCATEGORY: string
      MTRGROUP: string
      MTRL: string
      MTRUNIT1: string
      NAME: string
      PRICER: float
      PRICEW: float
      VAT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Services

```
POST /s1services/list/service
```

Use this endpoint in order to list all Services.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - SERVICE.CODE, SERVICE.CODE_TO
  - SERVICE.NAME, SERVICE.NAME_TO
  - SERVICE.MTRGROUP, SERVICE.MTRCATEGORY
  - SERVICE.ISACTIVE
- An example could be:
  - filters:'SERVICE.CODE=001 & SERVICE.CODE_TO=002 & SERVICE.NAME=ser & SERVICE.NAME_TO=ser & SERVICE.MTRGROUP=1 & SERVICE.MTRCATEGORY=1 & SERVICE.ISACTIVE=1'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are services, false otherwise
- totalCount is property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each service

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Service

```
POST /s1services/set/service
```

Use this endpoint in order to insert or update Service.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - SERVICE:CODE,CRDCARDMODE,EXPN1,EXPVAL1,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,VAT
- In appId use the appId that was provided to you .
- In locateinfo use entity names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - SERVICE:CODE,CRDCARDMODE,EXPN1,EXPVAL1,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,VAT
- An example could be:
  - locateinfo:"SERVICE:CODE,CRDCARDMODE,EXPN1,EXPVAL1,ISACTIVE,MTRCATEGORY,MTRGROUP,MTRL,MTRUNIT1,NAME,PRICER,PRICEW,REMARKS,VAT"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  data : {
    SERVICE : [{
      PRICER: float
      MTRUNIT1: integer
      PRICEW: float
      EXPVAL3: float
      VAT: integer
      EXPVAL2: float
      CRDCARDMODE: integer
      EXPVAL1: float
      REMARKS: string
      NAME: string
      CODE: string
      EXPN2: float
      EXPN1: float
      MTRGROUP: integer
      EXPN3: float
      MTRCATEGORY: integer
    }]
  }
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the service inserted or updated (true / false)
- id the id of service
- data is te object with the entities that you request in locateinfo
- caption is the code and the name of Service delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    SERVICE : [{
      CODE: string
      CRDCARDMODE: string
      EXPN1: string
      EXPN2: string
      EXPN3: string
      EXPVAL1: string
      EXPVAL2: string
      EXPVAL3: string
      ISACTIVE: string
      MTRCATEGORY: string
      MTRGROUP: string
      MTRL: string
      MTRUNIT1: string
      NAME: string
      PRICER: string
      PRICEW: string
      VAT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

# Sales Documents

## Delete Sales Document

```
POST /s1services/del/saldoc
```

Use this endpoint in order to delete Sales Document.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the sales document deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Sales Document

```
POST /s1services/get/saldoc
```

Use this endpoint in order to get Sale Document.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - SALDOC:BRANCH,CMPFINCODE,DISC1VAL,EXPN,FINDOC,FINDOC_SALMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SHIPKIND,SHIPMENT,SUMAMNT,TRDR,TRDR_CUSTOMER_AFM,TRDR_CUSTOMER_CODE,TRDR_CUSTOMER_NAME,TRDR_CUSTOMER_PHONE01,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,SOSOURCE,ISPRINT,ISCANCEL,FINCODE,SERIES_SERIES_CHKPRINTDOC,TRDR_CUSTOMER_EMAIL,TRDR_CUSTOMER_IRSDATA,TRDR_CUSTOMER_JOBTYPETRD,TRDR_CUSTOMER_ADDRESS,TRDR_CUSTOMER_DISTRICT,TRDR_CUSTOMER_ZIP,TRDR_CUSTOMER_VATPROVISIONS
  - ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,MTRLINES,FINDOC,VAT_VAT_PERCNT,MTRUNIT
  - SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - VATANAL:LINENUM,VAT,SUBVAL,VATVAL,VAT_VAT_PERCNT
  - EXPANAL:EXPN,EXPVAL,EXPVATVAL,LINENUM
  - MTRDOC:DELIVDATE,ORDERTRDR,ORDERTRDR_CUSTOMER_CODE,ORDERTRDR_CUSTOMER_NAME,SHIPPINGADDR,SHIPTRDR,SHIPTRDR_CUSTOMER_CODE,SHIPTRDR_CUSTOMER_NAME,SHPCITY,SHPDISTRICT,SHPZIP
- An example could be:
  - locateinfo:"SALDOC:BRANCH,CMPFINCODE,DISC1VAL,EXPN,FINDOC,FINDOC_SALMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SHIPKIND,SHIPMENT,SUMAMNT,TRDR,TRDR_CUSTOMER_AFM,TRDR_CUSTOMER_CODE,TRDR_CUSTOMER_NAME,TRDR_CUSTOMER_PHONE01,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,SOSOURCE,ISPRINT,ISCANCEL,FINCODE,SERIES_SERIES_CHKPRINTDOC,TRDR_CUSTOMER_EMAIL,TRDR_CUSTOMER_IRSDATA,TRDR_CUSTOMER_JOBTYPETRD,TRDR_CUSTOMER_ADDRESS,TRDR_CUSTOMER_DISTRICT,TRDR_CUSTOMER_ZIP,TRDR_CUSTOMER_VATPROVISIONS;ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,MTRLINES,FINDOC,VAT_VAT_PERCNT,MTRUNIT;SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT;VATANAL:LINENUM,VAT,SUBVAL,VATVAL,VAT_VAT_PERCNT;EXPANAL:EXPN,EXPVAL,EXPVATVAL,LINENUM;MTRDOC:DELIVDATE,ORDERTRDR,ORDERTRDR_CUSTOMER_CODE,ORDERTRDR_CUSTOMER_NAME,SHIPPINGADDR,SHIPTRDR,SHIPTRDR_CUSTOMER_CODE,SHIPTRDR_CUSTOMER_NAME,SHPCITY,SHPDISTRICT,SHPZIP"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if sales document found false otherwise
- data is the object with the entities that you request for example SALDOC
- caption is the code and the name of sale delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    SALDOC : [{
      BRANCH: string
      CMPFINCODE: string
      DISC1VAL: string
      EXPN: string
      NETAMNT: string
      PAYMENT: string
      SERIES: string
      SUMAMNT: string
      TRNDATE: string
      VATAMNT: string
      VATPROVISIONS: string
      VATSTS: string
    }]
    MTRDOC : [{
      SHIPPINGADDR: string
      SHPCITY: string
      SHPDISTRICT: string
      SHPZIP: string
    }]
    ITELINES : [{
      DISC1PRC: string
      LINENUM: string
      LINEVAL: string
      MTRL: string
      MTRL_ITEM_CODE: string
      MTRL_ITEM_NAME: string
      PRICE: string
      QTY1: string
      VAT: string
      MTRUNIT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Sales Documents

```
POST /s1services/list/saldoc
```

Use this endpoint in order to list all Sales Documents.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - FINDOC.TRNDATE, FINDOC.TRNDATE_TO
  - SALDOC.SERIES
  - FINDOC.TRDR
  - SALDOC.BRANCH
  - FINDOC.FINCODE
- An example could be:
  - filters:'FINDOC.TRNDATE=2021-02-01 00:00 & FINDOC.TRNDATE_TO=2021-02-28 00:00:00 & SALDOC.SERIES=1 & FINDOC.TRDR=2 & SALDOC.BRANCH=1000 & FINDOC.FINCODE=0001'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are sales documents, false otherwise
- totalCount property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each sales document

text/html

object

Multiline description

{

success: boolean

upddate: string

reqID: string

totalcount: int32

fields : [{

name: string

type: string

}]

rows : [[

]]

}

---

## Insert or Update Sales Document

```
POST /s1services/set/saldoc
```

Use this endpoint in order to insert or update Sales Document.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - SALDOC:BRANCH,CMPFINCODE,DISC1VAL,EXPN,FINDOC,FINDOC_SALMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SHIPKIND,SHIPMENT,SUMAMNT,TRDR,TRDR_CUSTOMER_AFM,TRDR_CUSTOMER_CODE,TRDR_CUSTOMER_NAME,TRDR_CUSTOMER_PHONE01,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,SOSOURCE,ISPRINT,ISCANCEL,FINCODE,SERIES_SERIES_CHKPRINTDOC,TRDR_CUSTOMER_EMAIL,TRDR_CUSTOMER_IRSDATA,TRDR_CUSTOMER_JOBTYPETRD,TRDR_CUSTOMER_ADDRESS,TRDR_CUSTOMER_DISTRICT,TRDR_CUSTOMER_ZIP,TRDR_CUSTOMER_VATPROVISIONS
  - ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,MTRLINES,FINDOC,VAT_VAT_PERCNT,MTRUNIT
  - SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - VATANAL:LINENUM,VAT,SUBVAL,VATVAL,VAT_VAT_PERCNT
  - EXPANAL:EXPN,EXPVAL,EXPVATVAL,LINENUM
  - MTRDOC:DELIVDATE,ORDERTRDR,ORDERTRDR_CUSTOMER_CODE,ORDERTRDR_CUSTOMER_NAME,SHIPPINGADDR,SHIPTRDR,SHIPTRDR_CUSTOMER_CODE,SHIPTRDR_CUSTOMER_NAME,SHPCITY,SHPDISTRICT,SHPZIP
- In appId use the appId that was provided to you .
- In locateinfo Use entity names followed by fields from which you want to receive data.
- The format that should use is the following:
  - name of entities: fields
- The entities and the fields that you can use are the same as the data:
  - SALDOC:BRANCH,CMPFINCODE,DISC1VAL,EXPN,FINDOC,FINDOC_SALMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SHIPKIND,SHIPMENT,SUMAMNT,TRDR,TRDR_CUSTOMER_AFM,TRDR_CUSTOMER_CODE,TRDR_CUSTOMER_NAME,TRDR_CUSTOMER_PHONE01,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,SOSOURCE,ISPRINT,ISCANCEL,FINCODE,SERIES_SERIES_CHKPRINTDOC,TRDR_CUSTOMER_EMAIL,TRDR_CUSTOMER_IRSDATA,TRDR_CUSTOMER_JOBTYPETRD,TRDR_CUSTOMER_ADDRESS,TRDR_CUSTOMER_DISTRICT,TRDR_CUSTOMER_ZIP,TRDR_CUSTOMER_VATPROVISIONS
  - ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,MTRLINES,FINDOC,VAT_VAT_PERCNT,MTRUNIT
  - SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - VATANAL:LINENUM,VAT,SUBVAL,VATVAL,VAT_VAT_PERCNT
  - EXPANAL:EXPN,EXPVAL,EXPVATVAL,LINENUM
  - MTRDOC:DELIVDATE,ORDERTRDR,ORDERTRDR_CUSTOMER_CODE,ORDERTRDR_CUSTOMER_NAME,SHIPPINGADDR,SHIPTRDR,SHIPTRDR_CUSTOMER_CODE,SHIPTRDR_CUSTOMER_NAME,SHPCITY,SHPDISTRICT,SHPZIP
  - An example could be:*
    - locateinfo:"SALDOC:BRANCH,CMPFINCODE,DISC1VAL,EXPN,FINDOC,FINDOC_SALMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SHIPKIND,SHIPMENT,SUMAMNT,TRDR,TRDR_CUSTOMER_AFM,TRDR_CUSTOMER_CODE,TRDR_CUSTOMER_NAME,TRDR_CUSTOMER_PHONE01,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,SOSOURCE,ISPRINT,ISCANCEL,FINCODE,SERIES_SERIES_CHKPRINTDOC,TRDR_CUSTOMER_EMAIL,TRDR_CUSTOMER_IRSDATA,TRDR_CUSTOMER_JOBTYPETRD,TRDR_CUSTOMER_ADDRESS,TRDR_CUSTOMER_DISTRICT,TRDR_CUSTOMER_ZIP,TRDR_CUSTOMER_VATPROVISIONS;ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,MTRLINES,FINDOC,VAT_VAT_PERCNT,MTRUNIT;SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT;VATANAL:LINENUM,VAT,SUBVAL,VATVAL,VAT_VAT_PERCNT;EXPANAL:EXPN,EXPVAL,EXPVATVAL,LINENUM;MTRDOC:DELIVDATE,ORDERTRDR,ORDERTRDR_CUSTOMER_CODE,ORDERTRDR_CUSTOMER_NAME,SHIPPINGADDR,SHIPTRDR,SHIPTRDR_CUSTOMER_CODE,SHIPTRDR_CUSTOMER_NAME,SHPCITY,SHPDISTRICT,SHPZIP"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  data : {
    SALDOC : [{
      PAYMENT: string
      SERIES: integer
      VATPROVISIONS: integer
      SHIPMENT: integer
      REMARKS: string
      TRDR: integer
      PRJC: integer
      SHIPKIND: integer
    }]
    ITELINES : [{
      LINENUM: integer
      MTRL: integer
      DISC1PRC: float
      COMMENTS: string
      QTY1: float
    }]
    MTRDOC : [{
      DELIVDATE: string
      SHPDISTRICT: string
      SHPZIP: string
      SHIPPINGADDR: string
      SHPCITY: string
    }]
  }
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the sales document inserted or updated (true) otherwise (false)
- id the id of sales document
- data is te object with the entities that you request in locateinfo
- caption is the code and the name of sales document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    SALDOC : [{
      BRANCH: string
      CMPFINCODE: string
      DISC1VAL: string
      EXPN: string
      NETAMNT: string
      PAYMENT: string
      PRJC: string
      REMARKS: string
      SERIES: string
      SUMAMNT: string
      TRDR: string
      TRNDATE: string
      VATAMNT: string
      VATPROVISIONS: string
      VATSTS: string
    }]
    MTRDOC : [{
      SHIPPINGADDR: string
      SHPCITY: string
      SHPDISTRICT: string
      SHPZIP: string
    }]
    ITELINES : [{
      DISC1PRC: string
      LINENUM: string
      LINEVAL: string
      MTRL: string
      MTRUNIT: string
      MTRL_ITEM_CODE: string
      MTRL_ITEM_CODE1: string
      MTRL_ITEM_NAME: string
      PRICE: string
      QTY1: string
      VAT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

# Einvoice

## Get Einvoice signature

```
POST /s1services/einvoice
```

Use this endpoint in order to get Einvoice signature.

### Body contents

- In appId use the appId that was provided to you .
- In key use the saldoc id.
- In token use the Password that was provided to you.
- In service use einvoice.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: string
  token: string
  service: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success whether einvoice was sent (true) otherwise (false) apart from AADE response
- data the object that AADE returns

`application/json; charset=windows-1253`

```
{
  success: boolean
  data : {
    integritySignature: string
    signature: string
    uid: string
    mark: integer
    authenticationCode: string
    myDataResponse: string
    status: string
    series: string
    number: string
    dateIssued: string
    success: boolean
    message: string
  }
}
```

---

# Purchases Documents

## Delete Purchases Document

```
POST /s1services/del/purdoc
```

Use this endpoint in order to delete Purchases Document.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the purchases document deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Purchases Document

```
POST /s1services/get/purdoc
```

Use this endpoint in order to get Purchases Document.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - PURDOC:BRANCH,CMPFINCODE,DISC1VAL,FINDOC,FINDOC_PURMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TAXSERIES,TAXSERIESNUM,TRDR,TRDR_SUPPLIER_AFM,TRDR_SUPPLIER_CODE,TRDR_SUPPLIER_NAME,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,ISCANCEL,FINCODE,TRDR_SUPPLIER_EMAIL,TRDR_SUPPLIER_IRSDATA,TRDR_SUPPLIER_JOBTYPETRD,TRDR_SUPPLIER_PHONE01,TRDR_SUPPLIER_ADDRESS,TRDR_SUPPLIER_DISTRICT,TRDR_SUPPLIER_ZIP
  - ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_APVCODE,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - VATANAL:LINENUM,SUBVAL,VAT,VATVAL,VAT_VAT_PERCNT
- An example could be:
  - locateinfo:"PURDOC:BRANCH,CMPFINCODE,DISC1VAL,FINDOC,FINDOC_PURMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TAXSERIES,TAXSERIESNUM,TRDR,TRDR_SUPPLIER_AFM,TRDR_SUPPLIER_CODE,TRDR_SUPPLIER_NAME,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,ISCANCEL,FINCODE,TRDR_SUPPLIER_EMAIL,TRDR_SUPPLIER_IRSDATA,TRDR_SUPPLIER_JOBTYPETRD,TRDR_SUPPLIER_PHONE01,TRDR_SUPPLIER_ADDRESS,TRDR_SUPPLIER_DISTRICT,TRDR_SUPPLIER_ZIP;ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_APVCODE,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT;SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT;VATANAL:LINENUM,SUBVAL,VAT,VATVAL,VAT_VAT_PERCNT"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if purchases document found false otherwise
- data is the object with the entities that you request for example PURDOC
- caption is the code and the name of purchases document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    PURDOC : [{
      BRANCH: string
      CMPFINCODE: string
      DISC1VAL: string
      NETAMNT: string
      PAYMENT: string
      PRJC: string
      REMARKS: string
      SERIES: string
      TAXSERIES: string
      SUMAMNT: string
      TRDR: string
      TRNDATE: string
      VATAMNT: string
      VATPROVISIONS: string
      VATSTS: string
    }]
    ITELINES : [{
      DISC1PRC: string
      LINENUM: string
      LINEVAL: string
      MTRL: string
      MTRL_ITEM_CODE: string
      MTRL_ITEM_CODE1: string
      MTRL_ITEM_NAME: string
      PRICE: string
      QTY1: string
      VAT: string
      MTRUNIT: string
    }]
    VATANAL : [{
      LINENUM: string
      SUBVAL: string
      VAT: string
      VATVAL: string
      VAT_VAT_PERCNT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Purchases Documents

```
POST /s1services/list/purdoc
```

Use this endpoint in order to list all Purchases Documents.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - FINDOC.TRNDATE, FINDOC.TRNDATE_TO
  - PURDOC.SERIES
  - FINDOC.TRDR
  - PURDOC.BRANCH
  - FINDOC.FINCODE
- An example could be:
  - filters:'FINDOC.TRNDATE=2021-02-01 00:00 & FINDOC.TRNDATE_TO=2021-02-28 00:00:00 & PURDOC.SERIES=1 & FINDOC.TRDR=2 & PURDOC.BRANCH=1000 & FINDOC.FINCODE=0001'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are documents, false otherwise
- totalCount is property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each purchases document

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Purchases Document

```
POST /s1services/set/purdoc
```

Use this endpoint in order to insert or update Purchases Document.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - PURDOC:BRANCH,CMPFINCODE,DISC1VAL,FINDOC,FINDOC_PURMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TAXSERIES,TAXSERIESNUM,TRDR,TRDR_SUPPLIER_AFM,TRDR_SUPPLIER_CODE,TRDR_SUPPLIER_NAME,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,ISCANCEL,FINCODE,TRDR_SUPPLIER_EMAIL,TRDR_SUPPLIER_IRSDATA,TRDR_SUPPLIER_JOBTYPETRD,TRDR_SUPPLIER_PHONE01,TRDR_SUPPLIER_ADDRESS,TRDR_SUPPLIER_DISTRICT,TRDR_SUPPLIER_ZIP
  - ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_APVCODE,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - VATANAL:LINENUM,SUBVAL,VAT,VATVAL,VAT_VAT_PERCNT
- In appId use the appId that was provided to you .
- In locateinfo Use entity names followed by fields from which you want to receive data.
- The format that should use is the following:
  - name of entities: fields
- The entities and the fields that you can use are the same as the data:
  - PURDOC:BRANCH,CMPFINCODE,DISC1VAL,FINDOC,FINDOC_PURMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TAXSERIES,TAXSERIESNUM,TRDR,TRDR_SUPPLIER_AFM,TRDR_SUPPLIER_CODE,TRDR_SUPPLIER_NAME,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,ISCANCEL,FINCODE,TRDR_SUPPLIER_EMAIL,TRDR_SUPPLIER_IRSDATA,TRDR_SUPPLIER_JOBTYPETRD,TRDR_SUPPLIER_PHONE01,TRDR_SUPPLIER_ADDRESS,TRDR_SUPPLIER_DISTRICT,TRDR_SUPPLIER_ZIP
  - ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_APVCODE,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT
  - VATANAL:LINENUM,SUBVAL,VAT,VATVAL,VAT_VAT_PERCNT
  - An example could be:*
    - locateinfo:"PURDOC:BRANCH,CMPFINCODE,DISC1VAL,FINDOC,FINDOC_PURMTRDOC_FINDOC,NETAMNT,PAYMENT,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TAXSERIES,TAXSERIESNUM,TRDR,TRDR_SUPPLIER_AFM,TRDR_SUPPLIER_CODE,TRDR_SUPPLIER_NAME,TRNDATE,VATAMNT,VATPROVISIONS,VATSTS,ISCANCEL,FINCODE,TRDR_SUPPLIER_EMAIL,TRDR_SUPPLIER_IRSDATA,TRDR_SUPPLIER_JOBTYPETRD,TRDR_SUPPLIER_PHONE01,TRDR_SUPPLIER_ADDRESS,TRDR_SUPPLIER_DISTRICT,TRDR_SUPPLIER_ZIP;ITELINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_ITEM_APVCODE,MTRL_ITEM_CODE,MTRL_ITEM_CODE1,MTRL_ITEM_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT;SRVLINES:COMMENTS,DISC1PRC,LINENUM,LINEVAL,MTRL,MTRL_SERVICE_CODE,MTRL_SERVICE_NAME,PRICE,QTY1,VAT,VAT_VAT_PERCNT,MTRUNIT;VATANAL:LINENUM,SUBVAL,VAT,VATVAL,VAT_VAT_PERCNT"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  key: string
  data : {
    PURDOC : [{
      BRANCH: integer
      SERIES: integer
      VATSTS: integer
      TRDR: integer
      PAYMENT: integer
      PRJC: integer
      VATPROVISIONS: integer
    }]
    ITELINES : [{
      LINENUM: integer
      MTRL: integer
      QTY1: float
    }]
  }
  locateinfo: string
  appId: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the purchases document inserted or updated (true) otherwise (false)
- id the id of purchases document
- data is te object with the entities that you request in locateinfo
- caption is the code and the name of purchases document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    PURDOC : [{
      BRANCH: string
      CMPFINCODE: string
      DISC1VAL: string
      NETAMNT: string
      PAYMENT: string
      PRJC: string
      SERIES: string
      SUMAMNT: string
      TAXSERIESNUM: string
      TRDR: string
      TRNDATE: string
      VATAMNT: string
      VATPROVISIONS: string
      VATSTS: string
    }]
    ITELINES : [{
      DISC1PRC: string
      LINENUM: string
      LINEVAL: string
      MTRL: string
      MTRL_ITEM_CODE: string
      MTRL_ITEM_CODE1: string
      MTRL_ITEM_NAME: string
      PRICE: string
      QTY1: string
      VAT: string
      MTRUNIT: string
    }]
    VATANAL : [{
      LINENUM: string
      SUBVAL: string
      VAT: string
      VATVAL: string
      VAT_VAT_PERCNT: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
  message: string
}
```

---

# Receipts Documents

## Delete Receipts Document

```
POST /s1services/del/cfncusdoc
```

Use this endpoint in order to delete Receipts Document.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the receipts document deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Receipts Document

```
POST /s1services/get/cfncusdoc
```

Use this endpoint in order to get Receipts Document.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - CFNCUSDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE,TRDR_CUSTOMER_EMAIL
  - CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL
  - CHEQUELINES:CHEQUE,CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS
- An example could be:
  - locateinfo:"CFNCUSDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE,TRDR_CUSTOMER_EMAIL;CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL;CHEQUELINES:CHEQUE,CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if receipts document found false otherwise
- data is the object with the entities that you request for example CFNCUSDOC
- caption is the code and the name of receipts document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    CFNCUSDOC : [{
      BRANCH: string
      CMPFINCODE: string
      COMPANY: string
      FINDOC: string
      NUM01: string
      PRJC: string
      PRJC_PRJC_CODE: string
      PRJC_PRJC_NAME: string
      SERIES: string
      SUMAMNT: string
      TRDR: string
      TRDR_TRDR_AFM: string
      TRDR_TRDR_CODE: string
      TRDR_TRDR_NAME: string
      TRNDATE: string
      TRDR_CUSTOMER_EMAIL: string
    }]
    CARDLINES : [{
      CRDCARDNUM: string
      CREDITCARDS: string
      LINENUM: string
      LINEVAL: string
    }]
    CHEQUELINES : [{
      CHEQUE: string
      CHEQUE_CHEQUE_CHEQUENUMBER: string
      CHEQUE_CHEQUE_CODE: string
      CHEQUE_CHEQUE_FINALDATE: string
      CHEQUE_CHEQUE_LCHEQUEVAL: string
      CODE: string
      LINENUM: string
      LINEVAL: string
      TPRMS: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Receipts Documents

```
POST /s1services/list/cfncusdoc
```

Use this endpoint in order to list all Receipts Documents.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - FINDOC.TRNDATE, FINDOC.TRNDATE_TO
  - FINDOC.TRDR
  - FINDOC.FINCODE
  - CFNCUSDOC.SERIES
  - CFNCUSDOC.BRANCH
- An example could be:
  - filters:'FINDOC.TRNDATE=2021-02-01 00:00 & FINDOC.TRNDATE_TO=2021-02-28 00:00:00 & CFNCUSDOC.SERIES=3800 & FINDOC.TRDR=1 & CFNCUSDOC.BRANCH=1000 & FINDOC.FINCODE=ΑΕ00001'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are receipts documents, false otherwise
- totalCount is property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each receipts document

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Receipts Document

```
POST /s1services/set/cfncusdoc
```

Use this endpoint in order to insert or update Receipts Document.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - CFNCUSDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE,TRDR_CUSTOMER_EMAIL
  - CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL
  - CHEQUELINES:CHEQUE,CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS
- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - CFNCUSDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE,TRDR_CUSTOMER_EMAIL
  - CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL
  - CHEQUELINES:CHEQUE,CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS
- An example could be:
  - locateinfo:"CFNCUSDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE,TRDR_CUSTOMER_EMAIL;CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL;CHEQUELINES:CHEQUE,CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  key: string
  data : {
    CFNCUSDOC : [{
      BRANCH: int32
      SERIES: int32
      TRDR: int32
      PRJC: int32
      REMARKS: string
    }]
    CARDLINES : [{
      CREDITCARDS: int32
      LINENUM: int32
      CRDCARDNUM: string
      LINEVAL: number
    }]
    CHEQUELINES : [{
      TPRMS: int32
      LINENUM: int32
      CODE: string
    }]
  }
  appId: string
  token: string
  locateinfo: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the receipts document inserted or updated *(true) otherwise (false)
- id the id of receipts document
- data is the object with the entities that you request in locateinfo
- caption is the code and the name of receipts document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    CFNCUSDOC : [{
      BRANCH: string
      CMPFINCODE: string
      COMPANY: string
      FINDOC: string
      NUM01: string
      PRJC: string
      PRJC_PRJC_CODE: string
      PRJC_PRJC_NAME: string
      SERIES: string
      SUMAMNT: string
      TRDR: string
      TRDR_TRDR_AFM: string
      TRDR_TRDR_CODE: string
      TRDR_TRDR_NAME: string
      TRNDATE: string
      TRDR_CUSTOMER_EMAIL: string
    }]
    CARDLINES : [{
      CRDCARDNUM: string
      CREDITCARDS: string
      LINENUM: string
      LINEVAL: string
    }]
    CHEQUELINES : [{
      CHEQUE: string
      CHEQUE_CHEQUE_CHEQUENUMBER: string
      CHEQUE_CHEQUE_CODE: string
      CHEQUE_CHEQUE_FINALDATE: string
      CHEQUE_CHEQUE_LCHEQUEVAL: string
      CODE: string
      LINENUM: string
      LINEVAL: string
      TPRMS: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

# Payments Documents

## Delete Payments Document

```
POST /s1services/del/cfnsupdoc
```

Use this endpoint in order to delete Payments Document.

### Body contents

- In appId use the appId that was provided to you .
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the payment deleted *(true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---

## Get Payments Document

```
POST /s1services/get/cfnsupdoc
```

Use this endpoint in order to get Payments Document.

### Body contents

- In appId use the appId that was provided to you .
- In locateinfo use entities names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the following:
  - CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL
  - CFNSUPDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE
  - CHEQUELINES:CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS
    - locateinfo:"CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL;CFNSUPDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE;CHEQUELINES:CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS"
- In key use the key of record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  locateinfo: string
  key: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if payments document found false otherwise
- data is the object with the entities that you request for example CFNSUPDOC
- caption is the code and the name of payments document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  readOnly: boolean
  data : {
    CFNSUPDOC : [{
      BRANCH: string
      CMPFINCODE: string
      COMPANY: string
      FINDOC: string
      NUM01: string
      PRJC: string
      PRJC_PRJC_CODE: string
      PRJC_PRJC_NAME: string
      REMARKS: string
      SERIES: string
      SUMAMNT: string
      TRDR: string
      TRDR_TRDR_AFM: string
      TRDR_TRDR_CODE: string
      TRDR_TRDR_NAME: string
      TRNDATE: string
    }]
    CARDLINES : [{
      CRDCARDNUM: string
      CREDITCARDS: string
      LINENUM: string
      LINEVAL: string
    }]
    CHEQUELINES : [{
      CHEQUE: string
      CHEQUE_CHEQUE_CHEQUENUMBER: string
      CHEQUE_CHEQUE_CODE: string
      CHEQUE_CHEQUE_FINALDATE: string
      CHEQUE_CHEQUE_LCHEQUEVAL: string
      CODE: string
      LINENUM: string
      LINEVAL: string
      TPRMS: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

## List all Payments Documents

```
POST /s1services/list/cfnsupdoc
```

Use this endpoint in order to list all Payments Documents.

### Body contents

- In appId use the appId that was provided to you .
- In filters use the filters that you want to apply in list.
- Filter to possible fields:
  - FINDOC.TRNDATE, FINDOC.TRNDATE_TO
  - CFNSUPDOC.SERIES
  - FINDOC.TRDR
  - CFNSUPDOC.BRANCH
  - FINDOC.FINCODE
- An example could be:
  - filters:'FINDOC.TRNDATE=2021-02-01 00:00 & FINDOC.TRNDATE_TO=2021-02-28 00:00:00 & CFNSUPDOC.SERIES=1 & FINDOC.TRDR=2 & CFNSUPDOC.BRANCH=1000 & FINDOC.FINCODE=0001'
- If you want to apply many filters use as delimeter the &. Combine all or some of them depending on what you want. If you want all data without filters set empty value.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  appId: string
  filters: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success true if there are payments documents, false otherwise
- totalCount property returns the total number of records
- fields is the fields that the rows have
- rows is the data of each payments document

`application/json; charset=windows-1253`

```
{
  success: boolean
  upddate: string
  reqID: string
  totalcount: int32
  fields : [{
    name: string
    type: string
  }]
  rows : [[
  ]]
}
```

---

## Insert or Update Payments Document

```
POST /s1services/set/cfnsupdoc
```

Use this endpoint in order to insert or update Payments Document.

### Body contents

- In data use the entities of data that you want to insert.
- The entities and the fields that you can use are the following:
  - CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL
  - CFNSUPDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE
  - CHEQUELINES:CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS
- In appId use the appId that was provided to you .
- In locateinfo Use entity names followed by fields from which you want to receive data.
- The format is the following:
  - name of entities: fields
- The entities and the fields that you can use are the same as the data:
  - CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL
  - CFNSUPDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE
  - CHEQUELINES:CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS
  - An example could be:*
    - locateinfo:"CARDLINES:CRDCARDNUM,CREDITCARDS,LINENUM,LINEVAL;CFNSUPDOC:BRANCH,CMPFINCODE,COMPANY,FINDOC,NUM01,PRJC,PRJC_PRJC_CODE,PRJC_PRJC_NAME,REMARKS,SERIES,SUMAMNT,TRDR,TRDR_TRDR_AFM,TRDR_TRDR_CODE,TRDR_TRDR_NAME,TRNDATE;CHEQUELINES:CHEQUE_CHEQUE_CHEQUENUMBER,CHEQUE_CHEQUE_CODE,CHEQUE_CHEQUE_FINALDATE,CHEQUE_CHEQUE_LCHEQUEVAL,CODE,LINENUM,LINEVAL,TPRMS"
- In key use the key of the record that you need to modify, leave it with empty value if you want to insert a new record.
- In token use the Password that was provided to you.

### Request

**Body** — `application/json`

```
{
  key: string
  data : {
    CFNSUPDOC : [{
      BRANCH: string
      SERIES: string
      TRDR: string
      PRJC: string
      NUM01: number
      REMARKS: string
    }]
    CARDLINES : [{
      CREDITCARDS: string
      LINENUM: string
      CRDCARDNUM: string
      LINEVAL: string
    }]
    CHEQUELINES : [{
      TPRMS: string
      LINENUM: string
      CHEQUE: string
    }]
  }
  appId: string
  locateinfo: string
  token: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if the payments document inserted or updated (true) otherwise (false)
- id the id of payments document
- data is te object with the entities that you request in locateinfo
- caption is the code and the name of payments document delimetered with -

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: int32
  readOnly: boolean
  data : {
    CFNSUPDOC : [{
      BRANCH: string
      CMPFINCODE: string
      COMPANY: string
      FINDOC: string
      NUM01: string
      PRJC: string
      PRJC_PRJC_CODE: string
      PRJC_PRJC_NAME: string
      REMARKS: string
      SERIES: string
      SUMAMNT: string
      TRDR: string
      TRDR_TRDR_AFM: string
      TRDR_TRDR_CODE: string
      TRDR_TRDR_NAME: string
      TRNDATE: string
    }]
    CARDLINES : [{
      CRDCARDNUM: string
      CREDITCARDS: string
      LINENUM: string
      LINEVAL: string
    }]
    CHEQUELINES : [{
      CHEQUE: string
      CHEQUE_CHEQUE_CHEQUENUMBER: string
      CHEQUE_CHEQUE_CODE: string
      CHEQUE_CHEQUE_FINALDATE: string
      CHEQUE_CHEQUE_LCHEQUEVAL: string
      CODE: string
      LINENUM: string
      LINEVAL: string
      TPRMS: string
    }]
  }
  prtname: string
  caption: string
  calc: boolean
  einvoice: boolean
}
```

---

# Webhook

## Create or Modify a Webhook

```
POST /s1services/webhook/create
```

This operation triggers when a Webhook is created or updated.

### Body contents

- In TOKEN use the Password that was provided to you.
- In APPID use the appId that was provided to you.
- In SERVICE use createWebhook.
- In NAME use your name of your choice.
- In OBJECT use some of the following:
  - SALDOC, PURDOC, CFNCUSDOC, CFNSUPDOC for Documents
  - CUSTOMER, SUPPLIER for Traders
  - ITEM, SERVICE for Entities
- In CONDITION use the condition that will trigger the Webhook.
- An example could be:
  - CONDITION:'ITEM.PRICEW>100'
- Default value is 1.
- In EVENT use some of the following:
  - ONPOST : trigger the Webhook when an object with the defined name posted
  - ONINSERT : trigger the Webhook when an object with the defined name inserted
  - ONUPDATE : trigger the Webhook when an object with the defined name updated
  - ONDELETE : trigger the Webhook when an object with the defined name deleted
  - In case of ONPOST covers both ONINSERT and ONUPDATE
- In ADDRESS use your third party url that you want to be informed, when a Webhook is triggered.
- In PARAMS use json object that you will be responsed when the Webhook will be triggered.

### Request

**Body** — `application/json` (required)

```
{
  TOKEN: string
  APPID: integer
  SERVICE: string
  NAME: string
  OBJECT: string
  CONDITION: string
  EVENT: string
  ADDRESS: string
  PARAMS: string
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if Webhook created (true) otherwise (false)
- id is the id of the created Webhook that can be used in order to delete it

`application/json; charset=windows-1253`

```
{
  success: boolean
  id: integer
}
```

---

## Delete an existing Webhook

```
POST /s1services/webhook/del
```

This operation triggers when an existing Webhook is deleted.

### Body contents

- In TOKEN use the Password that was provided to you.
- In APPID use the appId that was provided to you .
- In SERVICE use deleteWebhook in order to delete an existing Webhook.
- In NAME use your name of your choice.
- In ID use the ID that returned when the Webhook created.

### Request

**Body** — `application/json` (required)

```
{
  TOKEN: string
  APPID: integer
  SERVICE: string
  NAME: string
  ID: integer
}
```

**Headers** — `s1code`: string (required)

In s1code use the Username that was provided to you.

Example: 10502454783619

### Response `200`

The fields that returned are:

- success if Webhook deleted (true) otherwise (false)

`application/json; charset=windows-1253`

```
{
  success: boolean
}
```

---
