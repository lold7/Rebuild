ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE orders ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders ADD COLUMN stock_released BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD CONSTRAINT orders_stock_release_status CHECK (NOT stock_released OR status = 'cancelled');
CREATE INDEX orders_admin_newest_idx ON orders(created_at DESC, order_id DESC);
CREATE INDEX orders_status_newest_idx ON orders(status, created_at DESC, order_id DESC);

CREATE TABLE order_status_changes (
    change_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    admin_user_id BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    previous_status VARCHAR(20) NOT NULL CHECK (previous_status IN ('pending', 'completed', 'cancelled')),
    new_status VARCHAR(20) NOT NULL CHECK (new_status IN ('pending', 'completed', 'cancelled')),
    order_version BIGINT NOT NULL CHECK (order_version > 0),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (previous_status <> new_status),
    UNIQUE (order_id, order_version)
);
CREATE INDEX order_status_changes_newest_idx ON order_status_changes(order_id, created_at DESC, change_id DESC);

ALTER TABLE inventory_adjustments ADD COLUMN order_id BIGINT REFERENCES orders(order_id) ON DELETE SET NULL;
CREATE INDEX inventory_adjustments_order_idx ON inventory_adjustments(order_id);
