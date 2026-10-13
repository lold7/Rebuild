const pool = require('../config/db');

module.exports = {
    getByUser: async (userId) => {
        const [rows] = await pool.query(
            'SELECT * FROM addresses WHERE user_id = ? ORDER BY is_default DESC, created_at DESC',
            [userId]
        );
        return rows;
    },

    getById: async (addressId, userId) => {
        const [rows] = await pool.query(
            'SELECT * FROM addresses WHERE address_id = ? AND user_id = ?',
            [addressId, userId]
        );
        return rows[0];
    },

    create: async (userId, data) => {
        const [result] = await pool.query(
            `INSERT INTO addresses (user_id, label, full_name, phone, address_line, city, province, postal_code, is_default)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [userId, data.label, data.full_name, data.phone, data.address_line, data.city, data.province, data.postal_code, data.is_default || false]
        );
        return result.insertId;
    },

    update: async (addressId, userId, data) => {
        await pool.query(
            `UPDATE addresses SET label = ?, full_name = ?, phone = ?, address_line = ?, city = ?, province = ?, postal_code = ?
             WHERE address_id = ? AND user_id = ?`,
            [data.label, data.full_name, data.phone, data.address_line, data.city, data.province, data.postal_code, addressId, userId]
        );
    },

    delete: async (addressId, userId) => {
        await pool.query('DELETE FROM addresses WHERE address_id = ? AND user_id = ?', [addressId, userId]);
    },

    setDefault: async (addressId, userId) => {
        const conn = await pool.getConnection();
        try {
            await conn.beginTransaction();
            await conn.query('UPDATE addresses SET is_default = FALSE WHERE user_id = ?', [userId]);
            await conn.query('UPDATE addresses SET is_default = TRUE WHERE address_id = ? AND user_id = ?', [addressId, userId]);
            await conn.commit();
        } catch (err) {
            await conn.rollback();
            throw err;
        } finally {
            conn.release();
        }
    }
};
