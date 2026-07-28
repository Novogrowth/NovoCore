# Flyway migrations

Flyway owns the schema from the first commit. Hibernate never generates DDL
(`ddl-auto: validate`), because the debits=credits invariant is a database constraint
trigger (`CLAUDE.md` rule 6) that Hibernate cannot express and would drop if it ever
regenerated the schema.

Applied migrations, in order: `V1` conventions and the audit log, `V2` settings, `V3`
attachments, `V4` the chart of accounts, `V5` VAT classes / exemption reasons / charge types,
`V6` users, roles and permissions, `V7` the two fee income accounts and the charge-type seed,
`V8` the real AADE exemption-reason seed, `V9` products, customers, suppliers and assets,
`V10` the VAT rate's lower bound, `V11` units of measure as a table, `V12` inventory lots and
serialized units, `V13` bundle products, `V14` the separate Output/Input VAT accounts and three
new system keys, `V15` journal entries, journal lines and stock write-offs.

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

   **Two kinds of monetary column, because the scale cannot identify them on its own.** Every
   `numeric(19,2)` is an amount and therefore money. A `numeric(19,6)` is a multiplier, and only
   *some* multipliers are money — a VAT rate and a quantity are not, a unit cost is. So the
   discriminator for the six-decimal case is the **name**: a monetary multiplier is named
   `..._cost`, and the test requires a currency companion for any `numeric(19,6)` column whose
   name ends that way. `inventory_lot.unit_cost` in `V12` is the first. A monetary six-decimal
   column that is genuinely not a cost means widening that rule, not naming the column around it.

6. **A cross-row invariant is a deferred constraint trigger, not stored totals.** `V15` is the
   first case: debits equal credits (`CLAUDE.md` rule 6) spans the rows of an entry, which a
   CHECK cannot express. It is `CREATE CONSTRAINT TRIGGER ... DEFERRABLE INITIALLY DEFERRED`,
   checked at **commit**, because an entry is legitimately unbalanced between its first line and
   its last. Denormalised `total_debits`/`total_credits` columns with a single-row CHECK would
   have been simpler and were rejected: they are a second copy of what the lines say, free to
   disagree with them. Note the consequence for tests — a raw-SQL probe has to write the entry and
   its lines inside **one statement** (a `DO` block), because under autocommit each statement is
   its own transaction and the deferred check would fire between them.

   `V15` also makes one **UNIQUE** constraint deferrable — `journal_line_number_unique_in_entry`.
   That is not about a cross-row invariant but about an intermediate state: amending an entry
   replaces its whole line list, so line number 0 is inserted while the old line number 0 is still
   present. Relying on Hibernate ordering orphan removals before inserts would be relying on a
   library's implementation detail to keep a schema constraint satisfiable.

7. **A rule that needs another table's row is a trigger, not Java.** `V15`'s
   `journal_line_agrees_with_its_account` checks three of these: a Control-account line carries a
   sub-ledger reference of the declared type, the referenced row actually exists (the reference is
   polymorphic, so no foreign key is possible), and a VAT class appears only on a VAT account.
   The existence check in particular could not be done in Java without the ledger depending on the
   inventory service, which already depends on the ledger — a bean cycle for a check the database
   makes directly. **A trigger's `RAISE` arrives as SQLSTATE `P0001`**, which Spring maps to
   `UncategorizedSQLException` rather than `DataIntegrityViolationException`; assert on the message
   in tests, not the exception type.
