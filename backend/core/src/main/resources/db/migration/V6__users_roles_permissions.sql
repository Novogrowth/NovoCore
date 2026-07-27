-- Users, roles, and the two-layer permission model from brief §7.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DATA AND WHAT IS CODE
-- ---------------------------------------------------------------------------------------
-- Brief §7 requires support for multiple custom roles from the start, so ROLES ARE DATA:
-- creating one is an operation in the application, not a migration.
--
-- What is being granted is NOT data. Which sections exist, and which individual fields can be
-- hidden, are determined by what the software has been built to do — they are Java enums
-- (Section, ProtectedField) stored here as their names. Getting that split the other way round
-- gives you either a migration to add a role, or a database row naming a screen that does not
-- exist and never will.
--
-- The enum names are constrained by CHECK rather than a foreign key to a lookup table, because
-- the authoritative list is the Java enum. A lookup table would be a second copy of it, and
-- two copies of a permission list is how a section ends up grantable but unenforced.
--
-- ---------------------------------------------------------------------------------------
-- ACCESS IS DEFAULT-DENY
-- ---------------------------------------------------------------------------------------
-- A role has no access to a section unless a row in role_section_grant says otherwise. So
-- "everything else is invisible to Remote/Order Staff" needs no enumeration, and stays true as
-- new sections are added — a new section is invisible until someone grants it, which is the
-- safe direction to fail.
--
-- Owner and Admin sidestep this with a full_access flag rather than a grant per section. That
-- is deliberate: with stored grants, a section added in a later release would be invisible to
-- the owner of the system until someone remembered to insert a row.

-- ---------------------------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------------------------

CREATE TABLE app_role (
    id           bigserial    PRIMARY KEY,
    name         varchar(80)  NOT NULL,
    description  varchar(400),

    -- Full access to every section, present and future. Bypasses role_section_grant.
    full_access  boolean      NOT NULL DEFAULT false,

    -- Seeded and unmodifiable through the application. Without this, removing
    -- USERS_AND_ROLES from the last role that has it locks everyone out of user
    -- administration with no route back in short of editing the database by hand.
    system_role  boolean      NOT NULL DEFAULT false,

    active       boolean      NOT NULL DEFAULT true,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   varchar(100) NOT NULL DEFAULT 'system',
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT app_role_name_unique    UNIQUE (name),
    CONSTRAINT app_role_name_not_blank CHECK (btrim(name) <> '')
);

COMMENT ON TABLE app_role IS
    'Roles are data (brief §7: multiple custom roles from the start). What they grant is code.';
COMMENT ON COLUMN app_role.full_access IS
    'Bypasses role_section_grant, so sections added later are visible to Owner/Admin at once.';

-- ---------------------------------------------------------------------------------------
-- Section grants — the outer permission layer
-- ---------------------------------------------------------------------------------------

CREATE TABLE role_section_grant (
    role_id       bigint      NOT NULL,
    -- A gr.novotrade.novocore.core.api.security.Section name.
    section       varchar(60) NOT NULL,
    -- A gr.novotrade.novocore.core.api.security.AccessLevel name. NONE is never stored: the
    -- absence of a row already means no access, and storing "grants nothing" rows would create
    -- two representations of the same state.
    access_level  varchar(10) NOT NULL,

    CONSTRAINT role_section_grant_pk PRIMARY KEY (role_id, section),
    CONSTRAINT role_section_grant_role_fk
        FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE CASCADE,

    CONSTRAINT role_section_grant_section_known CHECK (section IN (
        'CHART_OF_ACCOUNTS',
        'TAX_AND_CHARGES',
        'SETTINGS',
        'AUDIT_LOG',
        'USERS_AND_ROLES',
        'PRODUCTS',
        'CUSTOMERS',
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS')),

    CONSTRAINT role_section_grant_level_known CHECK (access_level IN ('VIEW', 'FULL'))
);

-- ---------------------------------------------------------------------------------------
-- Field restrictions — the inner permission layer
-- ---------------------------------------------------------------------------------------
-- Named fields, not free-text column names. A restriction stored as the string
-- 'lastPurchasePrice' would silently stop protecting anything the day that field was renamed,
-- and nothing would fail — the field would just become visible. As an enum it is a compile
-- error instead.

CREATE TABLE role_field_restriction (
    role_id          bigint      NOT NULL,
    -- A gr.novotrade.novocore.core.api.security.ProtectedField name.
    protected_field  varchar(80) NOT NULL,

    CONSTRAINT role_field_restriction_pk PRIMARY KEY (role_id, protected_field),
    CONSTRAINT role_field_restriction_role_fk
        FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE CASCADE,

    CONSTRAINT role_field_restriction_field_known CHECK (protected_field IN (
        'PRODUCT_LAST_PURCHASE_PRICE',
        'PRODUCT_SUPPLIER',
        'PRODUCT_SUPPLIER_SKU'))
);

-- ---------------------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------------------
-- app_user, not user: USER is reserved in SQL, and in PostgreSQL `SELECT * FROM user` returns
-- the session user rather than failing, so the collision would be silent.

CREATE TABLE app_user (
    id             bigserial    PRIMARY KEY,

    -- Lower-case only, enforced below and normalised by the service, so that a login cannot be
    -- defeated — or duplicated — by capitalisation.
    username       varchar(100) NOT NULL,
    display_name   varchar(150) NOT NULL,

    -- Carries its algorithm as a prefix: {bcrypt}$2a$10$...  That prefix is what makes a future
    -- move to a stronger algorithm possible without invalidating every existing password.
    -- Never selected out of here by the application: the core verifies passwords itself so the
    -- hash does not cross its boundary (see UserService.authenticate).
    password_hash  varchar(200) NOT NULL,

    role_id        bigint       NOT NULL,
    active         boolean      NOT NULL DEFAULT true,

    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     varchar(100) NOT NULL DEFAULT 'system',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT app_user_role_fk FOREIGN KEY (role_id) REFERENCES app_role (id),

    CONSTRAINT app_user_username_unique       UNIQUE (username),
    CONSTRAINT app_user_username_is_lowercase CHECK (username = lower(username)),
    CONSTRAINT app_user_username_format       CHECK (username ~ '^[a-z0-9._-]{3,100}$'),
    CONSTRAINT app_user_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT app_user_password_hash_not_blank CHECK (btrim(password_hash) <> '')
);

COMMENT ON COLUMN app_user.password_hash IS
    'Algorithm-prefixed hash. Never read by application code; the core verifies internally.';

CREATE INDEX app_user_role_idx ON app_user (role_id);

-- ---------------------------------------------------------------------------------------
-- Seed: the three roles brief §7 defines
-- ---------------------------------------------------------------------------------------
-- NO USERS ARE SEEDED. There is deliberately no default account and no default password —
-- the same stance as the database credential, which has no fallback either. The first Owner is
-- created at startup from NOVOCORE_INITIAL_OWNER_USERNAME / _PASSWORD, and the application
-- refuses to start if no user exists and those are unset. A seeded admin/admin would outlive
-- every good intention to change it.

INSERT INTO app_role (name, description, full_access, system_role) VALUES
    ('OWNER',
     'Unrestricted access to everything, including future sections. Cannot be modified.',
     true, true),
    ('ADMIN',
     'Unrestricted access to everything, including future sections. Cannot be modified.',
     true, true),
    -- Not a system role, deliberately: this is the operational role whose grants are most
    -- likely to need adjusting, so it stays editable at runtime. The two that must never be
    -- broken are locked; this one is not.
    ('REMOTE_ORDER_STAFF',
     'Home-based order staff. Sales Order Fulfillment, Customers and Back-in-Stock in full; '
     'Products view-only with cost and supplier fields hidden; everything else invisible.',
     false, false);

-- Remote/Order Staff section grants. Everything not listed is invisible by default-deny —
-- the chart of accounts, settings, the audit log, tax configuration and user administration
-- are all absent from this list and therefore unreachable for this role.
INSERT INTO role_section_grant (role_id, section, access_level)
SELECT r.id, grant_row.section, grant_row.access_level
FROM   app_role r,
       (VALUES ('SALES_ORDER_FULFILLMENT', 'FULL'),
               ('CUSTOMERS',               'FULL'),
               ('BACK_IN_STOCK_REMINDERS', 'FULL'),
               -- View-only. The field restrictions below narrow this further.
               ('PRODUCTS',                'VIEW'))
           AS grant_row (section, access_level)
WHERE  r.name = 'REMOTE_ORDER_STAFF';

-- The cost and supplier fields hidden from Remote/Order Staff within Products.
--
-- An order picker needs to know what a product is and what it sells for; they have no need to
-- know what it cost us or who supplies it. The product's regular selling price is NOT
-- restricted, for exactly that reason.
--
-- Products arrive in step 5. These rows are live configuration, not placeholders — when
-- ProductView is built it must consult RoleView.canSee for each of them.
INSERT INTO role_field_restriction (role_id, protected_field)
SELECT r.id, field_row.protected_field
FROM   app_role r,
       (VALUES ('PRODUCT_LAST_PURCHASE_PRICE'),
               ('PRODUCT_SUPPLIER'),
               ('PRODUCT_SUPPLIER_SKU'))
           AS field_row (protected_field)
WHERE  r.name = 'REMOTE_ORDER_STAFF';
