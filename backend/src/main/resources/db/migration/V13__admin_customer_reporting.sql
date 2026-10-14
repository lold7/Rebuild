CREATE INDEX users_customer_newest_idx ON users(created_at DESC, user_id DESC) WHERE role = 'customer';

-- One aggregate row per customer with orders; customers with no orders are included by the reporting LEFT JOIN.
-- Read current order headers instead of joining items, so multiple lines never multiply counts or totals.
CREATE VIEW admin_customer_order_statistics AS
SELECT o.user_id,
    count(*) AS order_count,
    count(*) FILTER (WHERE o.status = 'pending') AS pending_order_count,
    count(*) FILTER (WHERE o.status = 'completed') AS completed_order_count,
    count(*) FILTER (WHERE o.status = 'cancelled') AS cancelled_order_count,
    coalesce(sum(o.total_price) FILTER (WHERE o.status <> 'cancelled'), 0::numeric) AS order_value,
    coalesce(sum(o.total_price) FILTER (WHERE o.payment_status = 'paid'), 0::numeric) AS total_spent,
    max(o.created_at) AS last_order_at
FROM orders o JOIN users u ON u.user_id = o.user_id AND u.role = 'customer'
GROUP BY o.user_id;
