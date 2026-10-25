-- Almanook initial schema. See docs/data-model.md for the rationale behind every constraint.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    email         varchar(320) NOT NULL,
    password_hash varchar(120) NOT NULL,
    display_name  varchar(100) NOT NULL,
    theme         varchar(10)  NOT NULL DEFAULT 'SYSTEM',
    language      varchar(16)  NOT NULL DEFAULT 'en',
    timezone      varchar(64)  NOT NULL DEFAULT 'Europe/Warsaw',
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT app_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT app_user_theme_valid CHECK (theme IN ('DARK', 'LIGHT', 'SYSTEM'))
);
CREATE UNIQUE INDEX app_user_email_uk ON app_user (email);

CREATE TABLE image_upload (
    id           uuid PRIMARY KEY,
    owner_id     uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    content_type varchar(50)  NOT NULL,
    data         bytea        NOT NULL,
    size_bytes   integer      NOT NULL,
    sha256       varchar(64)  NOT NULL,
    created_at   timestamptz  NOT NULL,
    CONSTRAINT image_upload_type_valid CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT image_upload_size_valid CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
CREATE INDEX image_upload_owner_idx ON image_upload (owner_id);

CREATE TABLE card (
    id                    uuid PRIMARY KEY,
    owner_id              uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title                 varchar(200)  NOT NULL,
    description           varchar(1200),
    item_type             varchar(20)   NOT NULL,
    venue_name            varchar(200),
    address               varchar(300),
    city                  varchar(120),
    country               varchar(80),
    latitude              numeric(9, 6),
    longitude             numeric(9, 6),
    creator               varchar(200),
    release_year          integer,
    source_url            varchar(2048),
    canonical_url         varchar(2048),
    price_min             numeric(10, 2),
    price_max             numeric(10, 2),
    currency              varchar(3),
    price_note            varchar(200),
    price_source          varchar(10),
    event_start_date      date,
    event_end_date        date,
    event_dates_source    varchar(10),
    expires_on            date,
    expiration_source     varchar(10),
    opening_hours_note    varchar(300),
    cover_image_url       varchar(2048),
    cover_source          varchar(20),
    cover_attribution     varchar(300),
    cover_attribution_url varchar(2048),
    cover_license         varchar(100),
    cover_upload_id       uuid REFERENCES image_upload (id) ON DELETE SET NULL,
    original_input        text,
    enrichment            jsonb,
    version               bigint        NOT NULL DEFAULT 0,
    created_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL,

    CONSTRAINT card_id_owner_uk UNIQUE (id, owner_id),
    CONSTRAINT card_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT card_item_type_valid CHECK (item_type IN ('PLACE', 'MUSEUM', 'EXHIBITION', 'PERFORMANCE', 'CONCERT',
        'FESTIVAL', 'MARKET', 'FILM', 'RESTAURANT', 'ACTIVITY', 'EVENT', 'OTHER')),
    CONSTRAINT card_coordinates_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT card_latitude_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT card_longitude_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT card_release_year_range CHECK (release_year IS NULL OR release_year BETWEEN 1870 AND 2200),
    CONSTRAINT card_price_min_non_negative CHECK (price_min IS NULL OR price_min >= 0),
    CONSTRAINT card_price_range CHECK (price_max IS NULL OR (price_min IS NOT NULL AND price_max >= price_min)),
    CONSTRAINT card_currency_format CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT card_currency_with_price CHECK ((price_min IS NULL) = (currency IS NULL)),
    CONSTRAINT card_price_source_valid CHECK (price_source IS NULL OR price_source IN ('USER', 'SOURCE', 'AI')),
    CONSTRAINT card_price_source_with_price CHECK (price_min IS NOT NULL OR price_source IS NULL),
    CONSTRAINT card_event_dates_order CHECK (event_end_date IS NULL OR event_start_date IS NULL
        OR event_end_date >= event_start_date),
    CONSTRAINT card_event_dates_source_valid CHECK (event_dates_source IS NULL
        OR event_dates_source IN ('USER', 'SOURCE', 'AI')),
    CONSTRAINT card_event_dates_source_with_dates CHECK (
        (event_start_date IS NULL AND event_end_date IS NULL) = (event_dates_source IS NULL)),
    CONSTRAINT card_expiration_source_valid CHECK (expiration_source IS NULL
        OR expiration_source IN ('USER', 'SOURCE', 'AI')),
    CONSTRAINT card_expiration_source_with_date CHECK ((expires_on IS NULL) = (expiration_source IS NULL)),
    CONSTRAINT card_cover_source_valid CHECK (cover_source IS NULL
        OR cover_source IN ('PAGE', 'WIKIMEDIA', 'UNSPLASH', 'USER_URL', 'UPLOAD')),
    CONSTRAINT card_cover_consistent CHECK (
        (cover_source IS NULL AND cover_image_url IS NULL AND cover_upload_id IS NULL)
        OR (cover_source = 'UPLOAD' AND cover_upload_id IS NOT NULL)
        OR (cover_source <> 'UPLOAD' AND cover_image_url IS NOT NULL AND cover_upload_id IS NULL))
);

CREATE INDEX card_owner_expires_idx ON card (owner_id, expires_on);
CREATE INDEX card_owner_title_idx ON card (owner_id, lower(title));
CREATE UNIQUE INDEX card_owner_canonical_url_uk ON card (owner_id, canonical_url) WHERE canonical_url IS NOT NULL;
CREATE INDEX card_title_trgm_idx ON card USING gin (lower(title) gin_trgm_ops);
CREATE INDEX card_description_trgm_idx ON card USING gin (lower(description) gin_trgm_ops);
CREATE INDEX card_cover_upload_idx ON card (cover_upload_id) WHERE cover_upload_id IS NOT NULL;

CREATE TABLE card_season (
    card_id uuid        NOT NULL REFERENCES card (id) ON DELETE CASCADE,
    season  varchar(10) NOT NULL,
    PRIMARY KEY (card_id, season),
    CONSTRAINT card_season_valid CHECK (season IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER'))
);
CREATE INDEX card_season_season_idx ON card_season (season, card_id);

CREATE TABLE collection_entry (
    id            uuid PRIMARY KEY,
    user_id       uuid          NOT NULL,
    card_id       uuid          NOT NULL,
    status        varchar(10)   NOT NULL,
    personal_note varchar(1000),
    saved_at      timestamptz   NOT NULL,
    visited_at    timestamptz,
    removed_at    timestamptz,
    CONSTRAINT collection_entry_card_owner_fk FOREIGN KEY (card_id, user_id)
        REFERENCES card (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT collection_entry_user_card_uk UNIQUE (user_id, card_id),
    CONSTRAINT collection_entry_status_valid CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT collection_entry_removed_consistent CHECK ((status = 'REMOVED') = (removed_at IS NOT NULL))
);
CREATE INDEX collection_entry_listing_idx ON collection_entry (user_id, status, saved_at DESC);

CREATE TABLE discovery_entry (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL,
    card_id      uuid        NOT NULL,
    status       varchar(10) NOT NULL,
    created_at   timestamptz NOT NULL,
    dismissed_at timestamptz,
    saved_at     timestamptz,
    CONSTRAINT discovery_entry_card_owner_fk FOREIGN KEY (card_id, user_id)
        REFERENCES card (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT discovery_entry_user_card_uk UNIQUE (user_id, card_id),
    CONSTRAINT discovery_entry_status_valid CHECK (status IN ('PENDING', 'SAVED', 'DISMISSED')),
    CONSTRAINT discovery_entry_dismissed_consistent CHECK ((status = 'DISMISSED') = (dismissed_at IS NOT NULL)),
    CONSTRAINT discovery_entry_saved_consistent CHECK ((status = 'SAVED') = (saved_at IS NOT NULL))
);
CREATE INDEX discovery_entry_listing_idx ON discovery_entry (user_id, status, created_at DESC);
