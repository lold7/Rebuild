const bcrypt = require('bcrypt');
const userModel = require('../models/userModel');
const orderModel = require('../models/orderModel');
const wishlistModel = require('../models/wishlistModel');
const addressModel = require('../models/addressModel');
const categoryModel = require('../models/categoryModel');

module.exports = {
    profile: async (req, res) => {
        try {
            const user = await userModel.findById(req.session.user.user_id);
            const categories = await categoryModel.getVisible();
            res.render('webstore/profile', { profileUser: user, categories, error: null, success: null });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    updateProfile: async (req, res) => {
        try {
            const { username, email, phone } = req.body;
            await userModel.updateProfile(req.session.user.user_id, username, email, phone);
            req.session.user.username = username;
            req.session.user.email = email;
            req.session.user.phone = phone;
            const user = await userModel.findById(req.session.user.user_id);
            const categories = await categoryModel.getVisible();
            res.render('webstore/profile', { profileUser: user, categories, error: null, success: 'Profile updated successfully' });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    changePassword: async (req, res) => {
        try {
            const { currentPassword, newPassword, confirmPassword } = req.body;
            const user = await userModel.findById(req.session.user.user_id);
            const categories = await categoryModel.getVisible();

            const match = await bcrypt.compare(currentPassword, user.password_hash);
            if (!match) {
                return res.render('webstore/profile', { profileUser: user, categories, error: 'Current password is incorrect', success: null });
            }

            if (newPassword !== confirmPassword) {
                return res.render('webstore/profile', { profileUser: user, categories, error: 'New passwords do not match', success: null });
            }

            const passwordHash = await bcrypt.hash(newPassword, 10);
            await userModel.updatePassword(req.session.user.user_id, passwordHash);
            res.render('webstore/profile', { profileUser: user, categories, error: null, success: 'Password changed successfully' });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    orderHistory: async (req, res) => {
        try {
            const orders = await orderModel.getByUser(req.session.user.user_id);
            // Get items for each order
            for (const order of orders) {
                const fullOrder = await orderModel.getById(order.order_id);
                order.items = fullOrder.items || [];
            }
            const categories = await categoryModel.getVisible();
            res.render('webstore/orderHistory', { orders, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    orderDetail: async (req, res) => {
        try {
            const order = await orderModel.getById(req.params.id);
            if (!order || order.user_id !== req.session.user.user_id) {
                return res.status(404).send('Order not found');
            }
            res.json(order);
        } catch (err) {
            console.error(err);
            res.status(500).json({ error: 'Failed to load order' });
        }
    },

    wishlist: async (req, res) => {
        try {
            const items = await wishlistModel.getByUser(req.session.user.user_id);
            items.forEach(item => {
                if (typeof item.product_images === 'string') item.product_images = JSON.parse(item.product_images);
                if (typeof item.product_attributes === 'string') item.product_attributes = JSON.parse(item.product_attributes);
            });
            const categories = await categoryModel.getVisible();
            res.render('webstore/wishlist', { wishlistItems: items, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    toggleWishlist: async (req, res) => {
        try {
            const { product_id } = req.body;
            const added = await wishlistModel.toggle(req.session.user.user_id, product_id);
            const count = await wishlistModel.getCount(req.session.user.user_id);
            res.json({ success: true, added, wishlistCount: count });
        } catch (err) {
            console.error(err);
            res.status(500).json({ success: false });
        }
    },

    addressBook: async (req, res) => {
        try {
            const addresses = await addressModel.getByUser(req.session.user.user_id);
            const categories = await categoryModel.getVisible();
            res.render('webstore/addressBook', { addresses, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    addAddress: async (req, res) => {
        try {
            await addressModel.create(req.session.user.user_id, req.body);
            res.redirect('/account/addresses');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    editAddress: async (req, res) => {
        try {
            await addressModel.update(req.params.id, req.session.user.user_id, req.body);
            res.redirect('/account/addresses');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    deleteAddress: async (req, res) => {
        try {
            await addressModel.delete(req.params.id, req.session.user.user_id);
            res.redirect('/account/addresses');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    setDefaultAddress: async (req, res) => {
        try {
            await addressModel.setDefault(req.params.id, req.session.user.user_id);
            res.redirect('/account/addresses');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    }
};
