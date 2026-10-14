ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
CREATE UNIQUE INDEX products_sku_unique ON products(lower(btrim(product_attributes ->> 'sku')))
    WHERE jsonb_typeof(product_attributes -> 'sku') = 'string' AND btrim(product_attributes ->> 'sku') <> '';
CREATE INDEX products_admin_newest_idx ON products(created_at DESC, product_id DESC);
CREATE INDEX products_stock_idx ON products(product_quantity, product_id);

CREATE TABLE inventory_adjustments (
    adjustment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    admin_user_id BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    quantity_before INT NOT NULL CHECK (quantity_before >= 0),
    quantity_after INT NOT NULL CHECK (quantity_after >= 0),
    delta BIGINT GENERATED ALWAYS AS (quantity_after::bigint - quantity_before::bigint) STORED,
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX inventory_adjustments_product_newest_idx
    ON inventory_adjustments(product_id, created_at DESC, adjustment_id DESC);
