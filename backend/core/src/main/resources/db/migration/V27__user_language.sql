-- A per-user language preference, for the frontend to read from GET /api/me.
--
-- ---------------------------------------------------------------------------------------
-- WHY A COLUMN ON app_user AND NOT A SETTING
-- ---------------------------------------------------------------------------------------
-- The `setting` table is global operator configuration — SMTP credentials, the rounding
-- threshold, retention policy. A language preference is none of those: it belongs to one
-- person and must follow them from the shop counter to the laptop at home, which is exactly
-- what a per-user column gives and what browser storage would not.
--
-- ---------------------------------------------------------------------------------------
-- NULLABLE, AND NULL IS A REAL ANSWER
-- ---------------------------------------------------------------------------------------
-- NULL means "this person has not chosen", not "not filled in yet". There is deliberately no
-- default: seeding one would be picking a language on every existing user's behalf, and the
-- same argument that keeps a fallback VAT rate and a guessed depreciation rate out of this
-- schema applies here. The frontend falls back to its own default when this is absent, which
-- is a decision it is entitled to make and the database is not.
--
-- ---------------------------------------------------------------------------------------
-- THE FORMAT IS CONSTRAINED; THE SET OF LANGUAGES IS NOT
-- ---------------------------------------------------------------------------------------
-- Q47(b) settled that the backend does not localise anything: every message it produces stays
-- English for now. So the backend has no opinion about which languages exist — offering Greek,
-- English or both is the frontend's decision, and encoding a list here would be this schema
-- claiming support it does not have.
--
-- What IS checked is the shape: a lowercase ISO 639 primary subtag, optionally an uppercase
-- ISO 3166 region. That refuses free text and casing variants ('EL', 'el_GR', 'greek') without
-- naming a single language.
--
-- The trigger to revisit: the day the backend localises its own messages, the set of supported
-- languages becomes code — a Java enum, exactly as Section is — and this column gains a CHECK
-- listing it, the way role_section_grant.section already does.

ALTER TABLE app_user
    ADD COLUMN language varchar(8);

ALTER TABLE app_user
    ADD CONSTRAINT app_user_language_format
        CHECK (language IS NULL OR language ~ '^[a-z]{2,3}(-[A-Z]{2})?$');

COMMENT ON COLUMN app_user.language IS
    'BCP 47 language tag chosen by this user, or NULL if they have not chosen. A frontend '
    'display preference only — the backend does not localise its own messages (Q47b).';
