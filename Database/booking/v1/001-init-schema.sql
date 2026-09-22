--liquibase formatted sql

--changeset fuzis:booking-001-init-schema
CREATE TABLE ticket_sales (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    passenger_id UUID NOT NULL,
    sale_price NUMERIC(12, 2) NOT NULL,
    source_ticket_id BIGINT NULL,
    sale_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ticket_sales_ticket_id UNIQUE (ticket_id),
    CONSTRAINT ck_ticket_sales_sale_price_positive CHECK (sale_price > 0)
);

CREATE INDEX idx_ticket_sales_passenger_id ON ticket_sales(passenger_id);
CREATE INDEX idx_ticket_sales_source_ticket_id ON ticket_sales(source_ticket_id);
CREATE INDEX idx_ticket_sales_sale_date ON ticket_sales(sale_date);
