-- NovoCore baseline: numeric conventions and the audit log.
--
-- ---------------------------------------------------------------------------------------
-- NUMERIC CONVENTIONS  (CLAUDE.md rule 5, ADR 0005)
-- ---------------------------------------------------------------------------------------
-- Every monetary or quantitative column in this schema uses one of exactly three shapes.
-- Deviating from them is a bug, not a style choice, and a schema-convention test asserts
-- these hold once the first such columns exist.
--
--   posted monetary amount   numeric(19,2)   + a companion currency column, char(3)
--   unit cost                numeric(19,6)   wider: proportional landed-cost allocation
--                                            (brief §4) produces more than two decimals
--   quantity                 numeric(19,6)   not integer: unit of measure is a Product
--                                            field and coffee sells by weight
--
-- NEVER float, real, double precision, or PostgreSQL's `money` type. The first three lose
-- precision invisibly; `money` is locale-dependent and lossy. There is no exception.
--
-- PostgreSQL DOMAINs were considered for this, to state each scale in exactly one place.
-- Rejected for now: Hibernate runs with ddl-auto=validate, and how it reports a
-- domain-typed column through JDBC metadata is unverified. Introducing an unverified
-- dependency at the foundation of the schema is a poor trade for tidiness, so the scales
-- are stated literally here and pinned from Java by Money.SCALE and Quantity.SCALE.
--
-- ---------------------------------------------------------------------------------------
-- AUDIT COLUMNS
-- ---------------------------------------------------------------------------------------
-- Tables holding user-modifiable state carry created_at / created_by / updated_at /
-- updated_by. Values are set by Spring Data JPA auditing rather than a database trigger,
-- so there is a single writer and no chance of Java and a trigger disagreeing. The columns
-- are NOT NULL with sensible defaults so that a manual psql session cannot leave them
-- empty either.

-- ---------------------------------------------------------------------------------------
-- Audit log
-- ---------------------------------------------------------------------------------------
-- Records what was done, by whom, and when. Distinct from the audit *columns* above: those
-- say who last touched a row, this says what happened over time and survives the row being
-- changed again.
--
-- Deliberately not Hibernate Envers. Envers versions every row of every audited entity,
-- which is the wrong shape here: journal entries are immutable once posted (corrections are
-- reversing entries), so row-level versioning of the ledger would mostly record nothing,
-- while the things actually worth auditing are *actions* — who confirmed a suggested
-- customer match, who overrode a rounding difference, who changed an SMTP password. If full
-- row versioning is wanted later for a specific entity, Envers can be added alongside this.

CREATE TABLE audit_log (
    id           bigserial    PRIMARY KEY,
    occurred_at  timestamptz  NOT NULL DEFAULT now(),
    -- Username rather than a user id foreign key: the log must stay readable and truthful
    -- after a user is deleted or renamed, and a FK would either block that deletion or
    -- leave the log pointing at nothing.
    actor        varchar(100) NOT NULL,
    action       varchar(80)  NOT NULL,
    entity_type  varchar(80)  NOT NULL,
    -- Text, not bigint: entity keys are mostly bigint but Setting is keyed by name.
    entity_id    varchar(120),
    detail       jsonb,

    CONSTRAINT audit_log_actor_not_blank       CHECK (btrim(actor) <> ''),
    CONSTRAINT audit_log_action_not_blank      CHECK (btrim(action) <> ''),
    CONSTRAINT audit_log_entity_type_not_blank CHECK (btrim(entity_type) <> '')
);

COMMENT ON TABLE audit_log IS
    'Append-only record of auditable actions. Updates and deletes are refused by trigger.';

-- "What happened to this record?" — the common query, and the reason entity_id is indexed
-- together with its type rather than alone.
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX audit_log_occurred_at_idx ON audit_log (occurred_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor, occurred_at DESC);

-- Append-only enforced in the database, not merely by the absence of a service method that
-- updates it. An audit log that application code can quietly rewrite is not evidence of
-- anything, and the guarantee has to hold against a psql session too.
CREATE FUNCTION audit_log_is_append_only() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'audit_log is append-only: % is not permitted. Correct the record by appending a new entry.',
        TG_OP;
END;
$$;

CREATE TRIGGER audit_log_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();
