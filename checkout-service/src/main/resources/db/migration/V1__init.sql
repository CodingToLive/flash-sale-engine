CREATE TABLE sale_config (
    sku            VARCHAR(64) PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    initial_stock  INT NOT NULL,
    price_cents    INT NOT NULL,
    sale_starts_at TIMESTAMPTZ,
    sale_ends_at   TIMESTAMPTZ
);

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    sku             VARCHAR(64) NOT NULL REFERENCES sale_config(sku),
    user_id         VARCHAR(128) NOT NULL,
    quantity        INT NOT NULL DEFAULT 1,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    notified_at     TIMESTAMPTZ
);

CREATE INDEX idx_orders_sku ON orders (sku);
CREATE INDEX idx_orders_status ON orders (status);

INSERT INTO sale_config (sku, name, initial_stock, price_cents, sale_starts_at, sale_ends_at)
VALUES ('FLASH-SALE-ITEM', 'Limited Edition Mechanical Keyboard', 100, 7999, now(), now() + INTERVAL '7 days');
