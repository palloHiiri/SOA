--liquibase formatted sql

--changeset fuzis:tickets-001-init-schema
CREATE TABLE venues (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    train_set_id INTEGER NOT NULL,
    CONSTRAINT uq_venues_name UNIQUE (name),
    CONSTRAINT ck_venues_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_venues_train_set_id_positive CHECK (train_set_id > 0)
);

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    creation_date DATE NOT NULL DEFAULT CURRENT_DATE,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    carriage_number VARCHAR(16) NOT NULL,
    seat_number VARCHAR(3) NOT NULL,
    refundable BOOLEAN,
    type VARCHAR(16),
    CONSTRAINT ck_tickets_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_tickets_carriage_number_not_blank CHECK (btrim(carriage_number) <> ''),
    CONSTRAINT ck_tickets_seat_number_not_blank CHECK (btrim(seat_number) <> ''),
    CONSTRAINT ck_tickets_type CHECK (type IS NULL OR type IN ('VIP', 'USUAL', 'CHEAP')),
    CONSTRAINT uq_tickets_venue_carriage_seat UNIQUE (venue_id, carriage_number, seat_number)
);

CREATE TABLE price_histories (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    changed_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    base_price NUMERIC(12, 2),
    discount SMALLINT NOT NULL,
    CONSTRAINT ck_price_histories_base_price_positive
        CHECK (base_price IS NULL OR base_price > 0),
    CONSTRAINT ck_price_histories_discount_range
        CHECK (discount > 0 AND discount <= 100)
);

-- Inventory does not expose relational tables to Tickets. The service stores
-- the CDC snapshot in the same three-column shape as inventory.train_set_cdc.
CREATE TABLE train_set_cdc (
    train_set_id INTEGER NOT NULL,
    data JSONB NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (train_set_id, version)
);

CREATE INDEX idx_tickets_venue_id ON tickets(venue_id);
CREATE INDEX idx_tickets_creation_date ON tickets(creation_date);
CREATE INDEX idx_tickets_type ON tickets(type);
CREATE INDEX idx_tickets_refundable ON tickets(refundable);
CREATE INDEX idx_tickets_name ON tickets(name);

CREATE INDEX idx_venues_train_set_id ON venues(train_set_id);

CREATE INDEX idx_price_histories_ticket_changed_date
    ON price_histories(ticket_id, changed_date DESC);
CREATE INDEX idx_price_histories_changed_date
    ON price_histories(changed_date);
CREATE INDEX idx_price_histories_discount
    ON price_histories(discount);

-- The primary key already provides the required access path by train_set_id.
