const pool = require('../config/db');

module.exports = {
    getAll: async () => {
        const [rows] = await pool.query(
            `SELECT c.*, COUNT(p.product_id) AS product_count
             FROM categories c
             LEFT JOIN products p ON c.category_id = p.category_id
             GROUP BY c.category_id
             ORDER BY c.category_name ASC`
        );
        return rows;
    },

    getVisible: async () => {
        const [rows] = await pool.query('SELECT * FROM categories WHERE is_visible = TRUE ORDER BY category_name ASC');
        return rows;
    },

    getById: async (id) => {
        const [rows] = await pool.query('SELECT * FROM categories WHERE category_id = ?', [id]);
        return rows[0];
    },

    create: async (name, image) => {
        const [result] = await pool.query(
            'INSERT INTO categories (category_name, category_image) VALUES (?, ?)',
            [name, image || null]
        );
        return result.insertId;
    },

    update: async (id, name, image) => {
        await pool.query(
            'UPDATE categories SET category_name = ?, category_image = ? WHERE category_id = ?',
            [name, image || null, id]
        );
    },

    delete: async (id) => {
        await pool.query('DELETE FROM categories WHERE category_id = ?', [id]);
    },

    toggleVisibility: async (id) => {
        await pool.query('UPDATE categories SET is_visible = NOT is_visible WHERE category_id = ?', [id]);
    }
};
