# Flyway migrations

Flyway owns the schema from the first commit. Hibernate never generates DDL
(`ddl-auto: validate`), because the debits=credits invariant is a database constraint
trigger (`CLAUDE.md` rule 6) that Hibernate cannot express and would drop if it ever
regenerated the schema.

Applied migrations, in order: `V1` conventions and the audit log, `V2` settings, `V3`
attachments, `V4` the chart of accounts, `V5` VAT classes / exemption reasons / charge types,
`V6` users, roles and permissions, `V7` the two fee income accounts and the charge-type seed,
`V8` the real AADE exemption-reason seed, `V9` products, customers, suppliers and assets.

## Rules

1. **An applied migration is never edited.** `validate-on-migrate` is on, so editing one
   fails startup rather than leaving environments silently diverged. Fix forward with a new
   migration.
2. **Naming:** `V<n>__snake_case_description.sql`, single integer version, no gaps
   intentionally left. Repeatable migrations (`R__`) only for things that are genuinely
   idempotent redefinitions, such as a view or a trigger function body.
3. **Money and quantity columns use one of exactly two numeric shapes**: `numeric(19,2)` for a
   posted amount, `numeric(19,6)` for a multiplier — a quantity, a unit cost or a rate.
   PostgreSQL DOMAINs were considered for this and **rejected** in `V1`, because Hibernate runs
   with `ddl-auto: validate` and how it reports a domain-typed column through JDBC metadata is
   unverified; the scales are stated literally instead and pinned from Java by `Money.SCALE`
   and `Quantity.SCALE`. Never `float`, `real`, `double precision` or `money` — see
   `CLAUDE.md` rule 5. The PostgreSQL `money` type in particular is locale-dependent and
   lossy; it is banned. `SchemaConventionsIT` asserts all of this against the schema Flyway
   actually produced.
4. **Constraints belong here, not only in Java.** Anything described as "structurally
   enforced" in the brief must be a database constraint, so that it holds against a manual
   `psql` session and a future adapter alike — not just against code paths that happen to go
   through the service layer.
5. **Every monetary column is accompanied by a currency column** (ADR 0005), even though all
   Phase 1 logic is EUR-only. The naming is `<column>` and `<column>_currency char(3)`, settled
   by the schema's first monetary column, `product.selling_price` in `V9`. Tie the pair
   together with a biconditional CHECK so an amount cannot exist without its currency.
   `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency` enforces the pairing, and a JPA
   field mapping a `char(3)` column needs `@JdbcTypeCode(SqlTypes.CHAR)` or schema validation
   rejects it as a `varchar` mismatch.
