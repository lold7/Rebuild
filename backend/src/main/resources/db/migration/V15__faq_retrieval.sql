CREATE TABLE faqs (
    faq_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question VARCHAR(500) NOT NULL CHECK (length(trim(question)) > 0),
    answer TEXT NOT NULL CHECK (length(trim(answer)) > 0 AND length(answer) <= 10000),
    sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX faqs_visible_order_idx ON faqs(sort_order, faq_id) WHERE is_visible = TRUE;
