-- Align order handling with the original store:
--  * Cash on Delivery is a payment choice.
--  * Orders can be marked "processing" between pending and completed.
--  * Admins may reverse a cancellation (stock is deducted again by the application).
ALTER TABLE orders DROP CONSTRAINT orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check
    CHECK (payment_method IN ('CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'DIGITAL_WALLET', 'CASH_ON_DELIVERY'));

ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('pending', 'processing', 'completed', 'cancelled'));

ALTER TABLE order_status_changes DROP CONSTRAINT order_status_changes_previous_status_check;
ALTER TABLE order_status_changes ADD CONSTRAINT order_status_changes_previous_status_check
    CHECK (previous_status IN ('pending', 'processing', 'completed', 'cancelled'));
ALTER TABLE order_status_changes DROP CONSTRAINT order_status_changes_new_status_check;
ALTER TABLE order_status_changes ADD CONSTRAINT order_status_changes_new_status_check
    CHECK (new_status IN ('pending', 'processing', 'completed', 'cancelled'));

-- Reporting keeps three buckets; "pending" counts every open order (pending or processing).
CREATE OR REPLACE VIEW admin_customer_order_statistics AS
SELECT o.user_id,
    count(*) AS order_count,
    count(*) FILTER (WHERE o.status IN ('pending', 'processing')) AS pending_order_count,
    count(*) FILTER (WHERE o.status = 'completed') AS completed_order_count,
    count(*) FILTER (WHERE o.status = 'cancelled') AS cancelled_order_count,
    coalesce(sum(o.total_price) FILTER (WHERE o.status <> 'cancelled'), 0::numeric) AS order_value,
    coalesce(sum(o.total_price) FILTER (WHERE o.payment_status = 'paid'), 0::numeric) AS total_spent,
    max(o.created_at) AS last_order_at
FROM orders o JOIN users u ON u.user_id = o.user_id AND u.role = 'customer'
GROUP BY o.user_id;

CREATE OR REPLACE VIEW admin_dashboard_daily_sales AS
SELECT (o.created_at AT TIME ZONE 'UTC')::date AS sales_date,
    count(*) AS total_orders,
    count(*) FILTER (WHERE o.status IN ('pending', 'processing')) AS pending_orders,
    count(*) FILTER (WHERE o.status = 'completed') AS completed_orders,
    count(*) FILTER (WHERE o.status = 'cancelled') AS cancelled_orders,
    coalesce(sum(o.total_price) FILTER (WHERE o.status <> 'cancelled'), 0::numeric) AS order_value,
    coalesce(sum(o.total_price) FILTER (WHERE o.payment_status = 'paid'), 0::numeric) AS total_revenue
FROM orders o JOIN users u ON u.user_id = o.user_id AND u.role = 'customer'
GROUP BY (o.created_at AT TIME ZONE 'UTC')::date;
