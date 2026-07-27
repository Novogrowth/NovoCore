# Flyway migrations

Flyway owns the schema from the first commit. Hibernate never generates DDL
(`ddl-auto: validate`), because the debits=credits invariant is a database constraint
trigger (`CLAUDE.md` rule 6) that Hibernate cannot express and would drop if it ever
regenerated the schema.

There are deliberately **no migrations yet** — the Phase 1 skeleton has no tables. The first
migration arrives with the shared primitives and numeric conventions in build step 2. Flyway
running with zero migrations is fine: it creates its history table and stops.

## Rules

1. **An applied migration is never edited.** `validate-on-migrate` is on, so editing one
   fails startup rather than leaving environments silently diverged. Fix forward with a new
   migration.
2. **Naming:** `V<n>__snake_case_description.sql`, single integer version, no gaps
   intentionally left. Repeatable migrations (`R__`) only for things that are genuinely
   idempotent redefinitions, such as a view or a trigger function body.
3. **Money and quantity columns use the shared PostgreSQL domains** introduced in step 2
   rather than bare `numeric(...)`, so the scale decision lives in exactly one place.
   Never `float`, `real`, `double precision` or `money` — see `CLAUDE.md` rule 5. The
   PostgreSQL `money` type in particular is locale-dependent and lossy; it is banned.
4. **Constraints belong here, not only in Java.** Anything described as "structurally
   enforced" in the brief must be a database constraint, so that it holds against a manual
   `psql` session and a future adapter alike — not just against code paths that happen to go
   through the service layer.
5. **Every monetary column is accompanied by a currency column** (ADR 0005), even though all
   Phase 1 logic is EUR-only.
