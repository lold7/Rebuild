const pool = require('../config/db');

module.exports = {
    getByUser: async (userId) => {
        const [rows] = await pool.query(
            `SELECT ci.*, p.product_name, p.author, p.product_price, p.product_images
             FROM cart_items ci
             JOIN products p ON ci.product_id = p.product_id
             WHERE ci.user_id = ?`,
            [userId]
        );
        return rows;
    },

    addItem: async (userId, productId, quantity, attributes) => {
        // Check if item already exists with same attributes
        const [existing] = await pool.query(
            'SELECT * FROM cart_items WHERE user_id = ? AND product_id = ? AND selected_attributes = ?',
            [userId, productId, JSON.stringify(attributes)]
        );
        if (existing.length > 0) {
            await pool.query(
                'UPDATE cart_items SET quantity = quantity + ? WHERE cart_item_id = ?',
                [quantity, existing[0].cart_item_id]
            );
            return existing[0].cart_item_id;
        } else {
            const [result] = await pool.query(
                'INSERT INTO cart_items (user_id, product_id, quantity, selected_attributes) VALUES (?, ?, ?, ?)',
                [userId, productId, quantity, JSON.stringify(attributes)]
            );
            return result.insertId;
        }
    },

    updateQuantity: async (cartItemId, userId, quantity) => {
        if (quantity <= 0) {
            await pool.query('DELETE FROM cart_items WHERE cart_item_id = ? AND user_id = ?', [cartItemId, userId]);
        } else {
            await pool.query(
                'UPDATE cart_items SET quantity = ? WHERE cart_item_id = ? AND user_id = ?',
                [quantity, cartItemId, userId]
            );
        }
    },

    removeItem: async (cartItemId, userId) => {
        await pool.query('DELETE FROM cart_items WHERE cart_item_id = ? AND user_id = ?', [cartItemId, userId]);
    },

    clearCart: async (userId) => {
        await pool.query('DELETE FROM cart_items WHERE user_id = ?', [userId]);
    },

    getCount: async (userId) => {
        const [rows] = await pool.query(
            'SELECT COALESCE(SUM(quantity), 0) as count FROM cart_items WHERE user_id = ?',
            [userId]
        );
        return rows[0].count;
    }
};
