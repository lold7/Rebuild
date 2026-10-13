const cartModel = require('../models/cartModel');
const orderModel = require('../models/orderModel');
const addressModel = require('../models/addressModel');
const categoryModel = require('../models/categoryModel');

module.exports = {
    viewCart: async (req, res) => {
        try {
            const cartItems = await cartModel.getByUser(req.session.user.user_id);
            const categories = await categoryModel.getVisible();
            // Parse JSON fields
            cartItems.forEach(item => {
                if (typeof item.product_images === 'string') {
                    item.product_images = JSON.parse(item.product_images);
                }
                if (typeof item.selected_attributes === 'string') {
                    item.selected_attributes = JSON.parse(item.selected_attributes);
                }
            });
            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            res.render('webstore/cart', { cartItems, total, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    addItem: async (req, res) => {
        try {
            const { product_id, quantity, attributes } = req.body;
            const parsedAttributes = attributes ? (typeof attributes === 'string' ? JSON.parse(attributes) : attributes) : {};
            await cartModel.addItem(req.session.user.user_id, product_id, parseInt(quantity) || 1, parsedAttributes);

            if (req.headers['x-requested-with'] === 'XMLHttpRequest') {
                const count = await cartModel.getCount(req.session.user.user_id);
                return res.json({ success: true, cartCount: count });
            }
            res.redirect('/cart');
        } catch (err) {
            console.error(err);
            if (req.headers['x-requested-with'] === 'XMLHttpRequest') {
                return res.status(500).json({ success: false, message: 'Failed to add item' });
            }
            res.status(500).send('Something went wrong');
        }
    },

    updateItem: async (req, res) => {
        try {
            const { cart_item_id, quantity } = req.body;
            await cartModel.updateQuantity(cart_item_id, req.session.user.user_id, parseInt(quantity));

            if (req.headers['x-requested-with'] === 'XMLHttpRequest') {
                const count = await cartModel.getCount(req.session.user.user_id);
                return res.json({ success: true, cartCount: count });
            }
            res.redirect('/cart');
        } catch (err) {
            console.error(err);
            if (req.headers['x-requested-with'] === 'XMLHttpRequest') {
                return res.status(500).json({ success: false });
            }
            res.status(500).send('Something went wrong');
        }
    },

    removeItem: async (req, res) => {
        try {
            const { cart_item_id } = req.body;
            await cartModel.removeItem(cart_item_id, req.session.user.user_id);

            if (req.headers['x-requested-with'] === 'XMLHttpRequest') {
                const count = await cartModel.getCount(req.session.user.user_id);
                return res.json({ success: true, cartCount: count });
            }
            res.redirect('/cart');
        } catch (err) {
            console.error(err);
            if (req.headers['x-requested-with'] === 'XMLHttpRequest') {
                return res.status(500).json({ success: false });
            }
            res.status(500).send('Something went wrong');
        }
    },

    checkout: async (req, res) => {
        try {
            const cartItems = await cartModel.getByUser(req.session.user.user_id);
            if (cartItems.length === 0) {
                return res.redirect('/cart');
            }
            cartItems.forEach(item => {
                if (typeof item.product_images === 'string') item.product_images = JSON.parse(item.product_images);
                if (typeof item.selected_attributes === 'string') item.selected_attributes = JSON.parse(item.selected_attributes);
            });
            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            const addresses = await addressModel.getByUser(req.session.user.user_id);
            const categories = await categoryModel.getVisible();
            res.render('webstore/checkout', { cartItems, total, addresses, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    choosePayment: async (req, res) => {
        try {
            const cartItems = await cartModel.getByUser(req.session.user.user_id);
            if (cartItems.length === 0) {
                return res.redirect('/cart');
            }
            cartItems.forEach(item => {
                if (typeof item.product_images === 'string') item.product_images = JSON.parse(item.product_images);
                if (typeof item.selected_attributes === 'string') item.selected_attributes = JSON.parse(item.selected_attributes);
            });
            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            const categories = await categoryModel.getVisible();
            res.render('webstore/choosePayment', { cartItems, total, categories, address_id: req.query.address_id });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    confirmOrder: async (req, res) => {
        try {
            const { address_id, payment_method } = req.body;
            const userId = req.session.user.user_id;
            const cartItems = await cartModel.getByUser(userId);

            if (cartItems.length === 0) {
                return res.redirect('/cart');
            }

            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            const orderItems = cartItems.map(item => ({
                product_id: item.product_id,
                quantity: item.quantity,
                unit_price: item.product_price,
                selected_attributes: typeof item.selected_attributes === 'string' ? JSON.parse(item.selected_attributes) : item.selected_attributes
            }));

            await orderModel.create(userId, address_id || null, total, payment_method || 'credit_card', orderItems);
            await cartModel.clearCart(userId);

            res.redirect('/account/orders');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    }
};
