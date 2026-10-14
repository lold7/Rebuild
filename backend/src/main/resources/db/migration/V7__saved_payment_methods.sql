CREATE TABLE payment_methods (
    payment_method_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    brand VARCHAR(30) NOT NULL CHECK (brand IN ('visa', 'mastercard', 'amex', 'discover', 'jcb', 'unionpay', 'diners', 'other')),
    cardholder_name VARCHAR(200) NOT NULL CHECK (length(trim(cardholder_name)) > 0),
    last4 CHAR(4) NOT NULL CHECK (last4 ~ '^[0-9]{4}$'),
    expiry_month INT NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year INT NOT NULL CHECK (expiry_year BETWEEN 2000 AND 9999),
    currency CHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$'),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX payment_methods_one_default_per_user ON payment_methods(user_id) WHERE is_default = TRUE;
CREATE INDEX payment_methods_user_newest_idx ON payment_methods(user_id, created_at DESC, payment_method_id DESC);
