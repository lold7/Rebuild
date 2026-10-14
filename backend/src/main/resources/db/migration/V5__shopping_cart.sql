CREATE TABLE cart_items (
    cart_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 10),
    selected_attributes JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(selected_attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (NOT (selected_attributes ? 'format') OR
        (jsonb_typeof(selected_attributes -> 'format') = 'string' AND length(selected_attributes ->> 'format') > 0))
);
CREATE UNIQUE INDEX cart_user_product_format_unique
    ON cart_items(user_id, product_id, lower(COALESCE(selected_attributes ->> 'format', '')));
CREATE INDEX cart_product_idx ON cart_items(product_id);
