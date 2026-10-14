CREATE INDEX orders_user_status_newest_idx ON orders(user_id, status, created_at DESC, order_id DESC);
