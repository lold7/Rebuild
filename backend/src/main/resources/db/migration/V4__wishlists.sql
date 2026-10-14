CREATE TABLE wishlist (
    wishlist_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT wishlist_user_product_unique UNIQUE (user_id, product_id)
);
CREATE INDEX wishlist_user_newest_idx ON wishlist(user_id, created_at DESC, wishlist_id DESC);
CREATE INDEX wishlist_product_idx ON wishlist(product_id);
