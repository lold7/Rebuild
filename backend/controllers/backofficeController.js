const bcrypt = require('bcrypt');
const productModel = require('../models/productModel');
const categoryModel = require('../models/categoryModel');
const orderModel = require('../models/orderModel');
const userModel = require('../models/userModel');
const addressModel = require('../models/addressModel');

module.exports = {
    dashboard: async (req, res) => {
        try {
            const stats = await orderModel.getDashboardStats();
            res.render('backoffice/dashboard', { stats });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    categories: async (req, res) => {
        try {
            const categories = await categoryModel.getAll();
            res.render('backoffice/categories', { categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    addCategory: async (req, res) => {
        try {
            const { category_name, category_image } = req.body;
            await categoryModel.create(category_name, category_image);
            res.redirect('/backoffice/categories');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    editCategory: async (req, res) => {
        try {
            const { category_name, category_image } = req.body;
            await categoryModel.update(req.params.id, category_name, category_image);
            res.redirect('/backoffice/categories');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    deleteCategory: async (req, res) => {
        try {
            await categoryModel.delete(req.params.id);
            res.redirect('/backoffice/categories');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    toggleCategory: async (req, res) => {
        try {
            await categoryModel.toggleVisibility(req.params.id);
            res.redirect('/backoffice/categories');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    products: async (req, res) => {
        try {
            const categories = await categoryModel.getAll();
            let products;
            const selectedCategory = req.query.category_id;
            if (selectedCategory) {
                products = await productModel.getByCategoryAdmin(selectedCategory);
            } else {
                products = await productModel.getAllAdmin();
            }
            products.forEach(p => {
                if (typeof p.product_images === 'string') p.product_images = JSON.parse(p.product_images);
            });
            res.render('backoffice/products', { products, categories, selectedCategory: selectedCategory || '' });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    inventory: async (req, res) => {
        try {
            const categories = await categoryModel.getAll();
            res.render('backoffice/inventory', { product: null, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    inventoryEdit: async (req, res) => {
        try {
            const product = await productModel.getById(req.params.id);
            if (!product) return res.status(404).send('Product not found');
            if (typeof product.product_images === 'string') product.product_images = JSON.parse(product.product_images);
            if (typeof product.product_attributes === 'string') product.product_attributes = JSON.parse(product.product_attributes);
            const categories = await categoryModel.getAll();
            res.render('backoffice/inventory', { product, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    addProduct: async (req, res) => {
        try {
            const { category_id, product_name, author, product_description, product_price, product_attributes } = req.body;
            const images = req.files ? req.files.map(f => f.filename) : [];
            await productModel.create({
                category_id,
                product_name,
                author,
                product_description,
                product_price,
                product_images: images,
                product_attributes: product_attributes ? JSON.parse(product_attributes) : {}
            });
            res.redirect('/backoffice/products');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    editProduct: async (req, res) => {
        try {
            const { category_id, product_name, author, product_description, product_price, product_attributes, existing_images } = req.body;
            const newImages = req.files ? req.files.map(f => f.filename) : [];
            const existingImages = existing_images ? (Array.isArray(existing_images) ? existing_images : [existing_images]) : [];
            const allImages = [...existingImages, ...newImages];

            await productModel.update(req.params.id, {
                category_id,
                product_name,
                author,
                product_description,
                product_price,
                product_images: allImages,
                product_attributes: product_attributes ? JSON.parse(product_attributes) : {}
            });
            res.redirect('/backoffice/products');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    deleteProduct: async (req, res) => {
        try {
            await productModel.delete(req.params.id);
            res.redirect('/backoffice/products');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    customers: async (req, res) => {
        try {
            const customers = await userModel.getAll();
            res.render('backoffice/customers', { customers });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    orders: async (req, res) => {
        try {
            let orders = await orderModel.getAll();
            const statusFilter = req.query.status;
            if (statusFilter) {
                orders = orders.filter(o => o.status === statusFilter);
            }
            // Get items for each order
            for (const order of orders) {
                const fullOrder = await orderModel.getById(order.order_id);
                order.items = fullOrder ? fullOrder.items : [];
                order.address = fullOrder ? fullOrder.address : null;
            }
            res.render('backoffice/orders', { orders, statusFilter: statusFilter || '' });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    updateOrderStatus: async (req, res) => {
        try {
            const { status } = req.body;
            await orderModel.updateStatus(req.params.id, status);
            res.redirect('/backoffice/orders');
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    adminProfile: async (req, res) => {
        try {
            const admin = await userModel.findById(req.session.user.user_id);
            res.render('backoffice/adminProfile', { admin, error: null, success: null });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    updateAdminProfile: async (req, res) => {
        try {
            const { username, email, phone } = req.body;
            await userModel.updateProfile(req.session.user.user_id, username, email, phone);
            req.session.user.username = username;
            req.session.user.email = email;
            const admin = await userModel.findById(req.session.user.user_id);
            res.render('backoffice/adminProfile', { admin, error: null, success: 'Profile updated successfully' });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    changeAdminPassword: async (req, res) => {
        try {
            const { currentPassword, newPassword, confirmPassword } = req.body;
            const admin = await userModel.findById(req.session.user.user_id);

            const match = await bcrypt.compare(currentPassword, admin.password_hash);
            if (!match) {
                return res.render('backoffice/adminProfile', { admin, error: 'Current password is incorrect', success: null });
            }
            if (newPassword !== confirmPassword) {
                return res.render('backoffice/adminProfile', { admin, error: 'New passwords do not match', success: null });
            }

            const passwordHash = await bcrypt.hash(newPassword, 10);
            await userModel.updatePassword(req.session.user.user_id, passwordHash);
            res.render('backoffice/adminProfile', { admin, error: null, success: 'Password changed successfully' });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    }
};
