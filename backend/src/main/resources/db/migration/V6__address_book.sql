CREATE TABLE addresses (
    address_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL DEFAULT 'Home' CHECK (length(trim(label)) > 0),
    full_name VARCHAR(200) NOT NULL CHECK (length(trim(full_name)) > 0),
    phone VARCHAR(20),
    address_line VARCHAR(500) NOT NULL CHECK (length(trim(address_line)) > 0),
    city VARCHAR(100) NOT NULL DEFAULT 'Bangkok',
    province VARCHAR(100) NOT NULL DEFAULT 'Bangkok',
    postal_code VARCHAR(10) NOT NULL DEFAULT '10110',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX addresses_one_default_per_user ON addresses(user_id) WHERE is_default = TRUE;
CREATE INDEX addresses_user_newest_idx ON addresses(user_id, created_at DESC, address_id DESC);
