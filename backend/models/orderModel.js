const pool = require('../config/db');

module.exports = {
    create: async (userId, addressId, totalPrice, paymentMethod, items) => {
        const conn = await pool.getConnection();
        try {
            await conn.beginTransaction();

            const [orderResult] = await conn.query(
                'INSERT INTO orders (user_id, address_id, total_price, payment_method, status) VALUES (?, ?, ?, ?, ?)',
                [userId, addressId, totalPrice, paymentMethod, 'completed']
            );
            const orderId = orderResult.insertId;

            for (const item of items) {
                await conn.query(
                    'INSERT INTO order_items (order_id, product_id, quantity, unit_price, selected_attributes) VALUES (?, ?, ?, ?, ?)',
                    [orderId, item.product_id, item.quantity, item.unit_price, JSON.stringify(item.selected_attributes || {})]
                );
            }

            await conn.commit();
            return orderId;
        } catch (err) {
            await conn.rollback();
            throw err;
        } finally {
            conn.release();
        }
    },

    getByUser: async (userId) => {
        const [rows] = await pool.query(
            'SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC',
            [userId]
        );
        return rows;
    },

    getById: async (orderId) => {
        const [orders] = await pool.query('SELECT * FROM orders WHERE order_id = ?', [orderId]);
        if (!orders[0]) return null;

        const [items] = await pool.query(
            `SELECT oi.*, p.product_name, p.author, p.product_images
             FROM order_items oi
             JOIN products p ON oi.product_id = p.product_id
             WHERE oi.order_id = ?`,
            [orderId]
        );

        const order = orders[0];
        order.items = items;

        // Get address info if available
        if (order.address_id) {
            const [addresses] = await pool.query('SELECT * FROM addresses WHERE address_id = ?', [order.address_id]);
            order.address = addresses[0] || null;
        }

        return order;
    },

    getAll: async () => {
        const [rows] = await pool.query(
            `SELECT o.*, u.username, u.email
             FROM orders o
             JOIN users u ON o.user_id = u.user_id
             ORDER BY o.created_at DESC`
        );
        return rows;
    },

    updateStatus: async (orderId, status) => {
        await pool.query('UPDATE orders SET status = ? WHERE order_id = ?', [status, orderId]);
    },

    getDashboardStats: async () => {
        const [totalOrders] = await pool.query('SELECT COUNT(*) as count FROM orders');
        const [totalRevenue] = await pool.query('SELECT COALESCE(SUM(total_price), 0) as total FROM orders WHERE status = ?', ['completed']);
        const [totalCustomers] = await pool.query('SELECT COUNT(*) as count FROM users WHERE role = ?', ['customer']);
        const [totalProducts] = await pool.query('SELECT COUNT(*) as count FROM products');
        const [recentOrders] = await pool.query(
            `SELECT o.*, u.username, u.email
             FROM orders o
             JOIN users u ON o.user_id = u.user_id
             ORDER BY o.created_at DESC LIMIT 20`
        );

        return {
            totalOrders: totalOrders[0].count,
            totalRevenue: totalRevenue[0].total,
            totalCustomers: totalCustomers[0].count,
            totalProducts: totalProducts[0].count,
            recentOrders
        };
    }
};
