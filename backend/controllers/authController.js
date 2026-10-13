const bcrypt = require('bcrypt');
const userModel = require('../models/userModel');
const categoryModel = require('../models/categoryModel');

module.exports = {
    loginPage: async (req, res) => {
        try {
            const categories = await categoryModel.getVisible();
            res.render('webstore/login', { categories, error: null });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    login: async (req, res) => {
        try {
            const { email, password } = req.body;
            const user = await userModel.findByEmail(email);

            if (!user || user.role !== 'customer') {
                const categories = await categoryModel.getVisible();
                return res.render('webstore/login', { categories, error: 'Invalid email or password' });
            }

            const match = await bcrypt.compare(password, user.password_hash);
            if (!match) {
                const categories = await categoryModel.getVisible();
                return res.render('webstore/login', { categories, error: 'Invalid email or password' });
            }

            req.session.user = {
                user_id: user.user_id,
                username: user.username,
                email: user.email,
                phone: user.phone,
                role: user.role
            };

            const returnTo = req.session.returnTo || '/';
            delete req.session.returnTo;
            res.redirect(returnTo);
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    registerPage: async (req, res) => {
        try {
            const categories = await categoryModel.getVisible();
            res.render('webstore/register', { categories, error: null });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    register: async (req, res) => {
        try {
            const { username, email, password, confirmPassword, phone } = req.body;

            if (!username || !email || !password) {
                const categories = await categoryModel.getVisible();
                return res.render('webstore/register', { categories, error: 'All fields are required' });
            }

            if (password !== confirmPassword) {
                const categories = await categoryModel.getVisible();
                return res.render('webstore/register', { categories, error: 'Passwords do not match' });
            }

            const existing = await userModel.findByEmail(email);
            if (existing) {
                const categories = await categoryModel.getVisible();
                return res.render('webstore/register', { categories, error: 'Email already registered' });
            }

            const passwordHash = await bcrypt.hash(password, 10);
            const userId = await userModel.create(username, email, passwordHash, phone);

            req.session.user = {
                user_id: userId,
                username,
                email,
                phone: phone || null,
                role: 'customer'
            };

            res.redirect('/');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    logout: (req, res) => {
        req.session.destroy(() => {
            res.redirect('/');
        });
    },

    // Admin auth
    adminLoginPage: (req, res) => {
        res.render('backoffice/login', { error: null });
    },

    adminLogin: async (req, res) => {
        try {
            const { email, password } = req.body;
            const user = await userModel.findByEmail(email);

            if (!user || user.role !== 'admin') {
                return res.render('backoffice/login', { error: 'Invalid admin credentials' });
            }

            const match = await bcrypt.compare(password, user.password_hash);
            if (!match) {
                return res.render('backoffice/login', { error: 'Invalid admin credentials' });
            }

            req.session.user = {
                user_id: user.user_id,
                username: user.username,
                email: user.email,
                phone: user.phone,
                role: user.role
            };

            res.redirect('/backoffice');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    adminLogout: (req, res) => {
        req.session.destroy(() => {
            res.redirect('/backoffice/login');
        });
    }
};
