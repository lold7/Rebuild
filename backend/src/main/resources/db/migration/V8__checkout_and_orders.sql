CREATE TABLE orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    address_id BIGINT REFERENCES addresses(address_id) ON DELETE SET NULL,
    saved_payment_method_id BIGINT REFERENCES payment_methods(payment_method_id) ON DELETE SET NULL,
    idempotency_key VARCHAR(100) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'),
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    address_snapshot JSONB NOT NULL CHECK (jsonb_typeof(address_snapshot) = 'object'),
    payment_snapshot JSONB CHECK (payment_snapshot IS NULL OR jsonb_typeof(payment_snapshot) = 'object'),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'DIGITAL_WALLET')),
    currency CHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    total_items INT NOT NULL CHECK (total_items > 0),
    total_quantity BIGINT NOT NULL CHECK (total_quantity > 0),
    total_price NUMERIC(20, 2) NOT NULL CHECK (total_price >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'completed', 'cancelled')),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'unpaid' CHECK (payment_status IN ('unpaid', 'paid', 'refunded')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, idempotency_key)
);
CREATE INDEX orders_user_newest_idx ON orders(user_id, created_at DESC, order_id DESC);
CREATE INDEX orders_address_idx ON orders(address_id);
CREATE INDEX orders_payment_method_idx ON orders(saved_payment_method_id);

CREATE TABLE order_items (
    order_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(product_id) ON DELETE SET NULL,
    product_snapshot JSONB NOT NULL CHECK (jsonb_typeof(product_snapshot) = 'object'),
    selected_attributes JSONB NOT NULL CHECK (jsonb_typeof(selected_attributes) = 'object'),
    quantity INT NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    line_total NUMERIC(20, 2) GENERATED ALWAYS AS (unit_price * quantity) STORED
);
CREATE INDEX order_items_order_idx ON order_items(order_id, order_item_id);
CREATE INDEX order_items_product_idx ON order_items(product_id);
