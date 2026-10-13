const pool = require('../config/db');

module.exports = {
    getAll: async () => {
        const [rows] = await pool.query(
            `SELECT p.*, c.category_name FROM products p
             JOIN categories c ON p.category_id = c.category_id
             WHERE c.is_visible = TRUE
             ORDER BY p.product_id`
        );
        return rows;
    },

    getById: async (id) => {
        const [rows] = await pool.query(
            `SELECT p.*, c.category_name FROM products p
             JOIN categories c ON p.category_id = c.category_id
             WHERE p.product_id = ?`,
            [id]
        );
        return rows[0];
    },

    getByCategory: async (categoryId) => {
        const [rows] = await pool.query(
            `SELECT p.*, c.category_name FROM products p
             JOIN categories c ON p.category_id = c.category_id
             WHERE p.category_id = ? AND c.is_visible = TRUE
             ORDER BY p.product_id`,
            [categoryId]
        );
        return rows;
    },

    search: async (query) => {
        const searchTerm = `%${query}%`;
        const [rows] = await pool.query(
            `SELECT p.*, c.category_name FROM products p
             JOIN categories c ON p.category_id = c.category_id
             WHERE c.is_visible = TRUE
             AND (p.product_name LIKE ? OR p.author LIKE ?)
             ORDER BY p.product_id`,
            [searchTerm, searchTerm]
        );
        return rows;
    },

    create: async (data) => {
        const [result] = await pool.query(
            `INSERT INTO products (category_id, product_name, author, product_description, product_price, product_images, product_attributes)
             VALUES (?, ?, ?, ?, ?, ?, ?)`,
            [data.category_id, data.product_name, data.author, data.product_description, data.product_price, JSON.stringify(data.product_images), JSON.stringify(data.product_attributes)]
        );
        return result.insertId;
    },

    update: async (id, data) => {
        await pool.query(
            `UPDATE products SET category_id = ?, product_name = ?, author = ?, product_description = ?, product_price = ?, product_images = ?, product_attributes = ?
             WHERE product_id = ?`,
            [data.category_id, data.product_name, data.author, data.product_description, data.product_price, JSON.stringify(data.product_images), JSON.stringify(data.product_attributes), id]
        );
    },

    delete: async (id) => {
        await pool.query('DELETE FROM products WHERE product_id = ?', [id]);
    },

    getAllAdmin: async () => {
        const [rows] = await pool.query(
            `SELECT p.*, c.category_name FROM products p
             JOIN categories c ON p.category_id = c.category_id
             ORDER BY p.product_id`
        );
        return rows;
    },

    getByCategoryAdmin: async (categoryId) => {
        const [rows] = await pool.query(
            `SELECT p.*, c.category_name FROM products p
             JOIN categories c ON p.category_id = c.category_id
             WHERE p.category_id = ?
             ORDER BY p.product_name DESC`,
            [categoryId]
        );
        return rows;
    }
};
