const pool = require('../config/db');

module.exports = {
    getByUser: async (userId) => {
        const [rows] = await pool.query(
            `SELECT w.*, p.product_name, p.author, p.product_price, p.product_images, p.product_attributes, c.category_name
             FROM wishlist w
             JOIN products p ON w.product_id = p.product_id
             JOIN categories c ON p.category_id = c.category_id
             WHERE w.user_id = ?`,
            [userId]
        );
        return rows;
    },

    toggle: async (userId, productId) => {
        const [existing] = await pool.query(
            'SELECT * FROM wishlist WHERE user_id = ? AND product_id = ?',
            [userId, productId]
        );
        if (existing.length > 0) {
            await pool.query('DELETE FROM wishlist WHERE user_id = ? AND product_id = ?', [userId, productId]);
            return false; // removed
        } else {
            await pool.query('INSERT INTO wishlist (user_id, product_id) VALUES (?, ?)', [userId, productId]);
            return true; // added
        }
    },

    isWishlisted: async (userId, productId) => {
        const [rows] = await pool.query(
            'SELECT 1 FROM wishlist WHERE user_id = ? AND product_id = ?',
            [userId, productId]
        );
        return rows.length > 0;
    },

    remove: async (userId, productId) => {
        await pool.query('DELETE FROM wishlist WHERE user_id = ? AND product_id = ?', [userId, productId]);
    },

    getCount: async (userId) => {
        const [rows] = await pool.query(
            'SELECT COUNT(*) as count FROM wishlist WHERE user_id = ?',
            [userId]
        );
        return rows[0].count;
    }
};
