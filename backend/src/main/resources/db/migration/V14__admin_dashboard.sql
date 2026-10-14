CREATE INDEX orders_created_utc_date_idx ON orders (((created_at AT TIME ZONE 'UTC')::date));

-- Creation-date cohorts with current statuses; these are not payment-date ledger totals.
-- Keep paid/non-cancelled value definitions aligned with admin_customer_order_statistics.
CREATE VIEW admin_dashboard_daily_sales AS
SELECT (o.created_at AT TIME ZONE 'UTC')::date AS sales_date,
    count(*) AS total_orders,
    count(*) FILTER (WHERE o.status = 'pending') AS pending_orders,
    count(*) FILTER (WHERE o.status = 'completed') AS completed_orders,
    count(*) FILTER (WHERE o.status = 'cancelled') AS cancelled_orders,
    coalesce(sum(o.total_price) FILTER (WHERE o.status <> 'cancelled'), 0::numeric) AS order_value,
    coalesce(sum(o.total_price) FILTER (WHERE o.payment_status = 'paid'), 0::numeric) AS total_revenue
FROM orders o JOIN users u ON u.user_id = o.user_id AND u.role = 'customer'
GROUP BY (o.created_at AT TIME ZONE 'UTC')::date;
