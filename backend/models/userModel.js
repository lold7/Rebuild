const pool = require('../config/db');

module.exports = {
    findByEmail: async (email) => {
        const [rows] = await pool.query('SELECT * FROM users WHERE email = ?', [email]);
        return rows[0];
    },

    findById: async (id) => {
        const [rows] = await pool.query('SELECT * FROM users WHERE user_id = ?', [id]);
        return rows[0];
    },

    create: async (username, email, passwordHash, phone) => {
        const [result] = await pool.query(
            'INSERT INTO users (username, email, password_hash, phone) VALUES (?, ?, ?, ?)',
            [username, email, passwordHash, phone || null]
        );
        return result.insertId;
    },

    updateProfile: async (userId, username, email, phone) => {
        await pool.query(
            'UPDATE users SET username = ?, email = ?, phone = ? WHERE user_id = ?',
            [username, email, phone || null, userId]
        );
    },

    updatePassword: async (userId, passwordHash) => {
        await pool.query(
            'UPDATE users SET password_hash = ? WHERE user_id = ?',
            [passwordHash, userId]
        );
    },

    getAll: async () => {
        const [rows] = await pool.query(
            'SELECT user_id, username, email, phone, created_at FROM users WHERE role = ? ORDER BY created_at DESC',
            ['customer']
        );
        return rows;
    }
};
