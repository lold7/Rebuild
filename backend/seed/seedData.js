/**
 * Seed Script — Populates the bookstore database
 * Run: npm run seed
 *
 * This script reads and executes the SQL from sql/init.sql
 * which contains the full schema + seed data.
 */

require('dotenv').config();
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

async function seed() {
    let connection;
    try {
        // Connect without specifying database first (to create it if needed)
        connection = await mysql.createConnection({
            host: process.env.DB_HOST || 'localhost',
            user: process.env.DB_USER || 'root',
            password: process.env.DB_PASSWORD || 'rootpassword',
            multipleStatements: true
        });

        console.log('Connected to MySQL server');

        // Read the init.sql file
        const sqlPath = path.join(__dirname, '..', 'sql', 'init.sql');
        const sql = fs.readFileSync(sqlPath, 'utf8');

        console.log('Executing init.sql...');

        // Execute the SQL (creates database, tables, and inserts seed data)
        await connection.query(sql);

        console.log('Database seeded successfully!');
        console.log('');
        console.log('Test accounts:');
        console.log('  Admin:    admin@bookstore.com / admin123');
        console.log('  Customer: john@example.com / test123');
        console.log('  Customer: jane@example.com / test123');

    } catch (err) {
        console.error('Seed failed:', err.message);
        process.exit(1);
    } finally {
        if (connection) {
            await connection.end();
        }
        process.exit(0);
    }
}

seed();
