const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const productModel = require('../models/productModel');
const categoryModel = require('../models/categoryModel');
const cartModel = require('../models/cartModel');
const orderModel = require('../models/orderModel');
const wishlistModel = require('../models/wishlistModel');
const addressModel = require('../models/addressModel');
const userModel = require('../models/userModel');

const JWT_SECRET = process.env.JWT_SECRET || 'inwza-jwt-secret-key-2026';

const CATEGORY_MAP = {
    fiction: 'Fiction',
    children: 'Children',
    nonfiction: 'Non-Fiction',
    mystery: 'Mystery & Thriller',
    science: 'Science & Technology'
};

const parseJsonFields = (product) => {
    if (!product) return product;
    const p = { ...product };
    if (typeof p.product_images === 'string') {
        try { p.product_images = JSON.parse(p.product_images); } catch (_) { p.product_images = []; }
    }
    if (typeof p.product_attributes === 'string') {
        try { p.product_attributes = JSON.parse(p.product_attributes); } catch (_) { p.product_attributes = {}; }
    }
    return p;
};

module.exports = {
    // ===== AUTH =====
    login: async (req, res) => {
        try {
            const { email, password } = req.body;
            if (!email || !password) {
                return res.status(400).json({ error: 'Email and password are required' });
            }
            const user = await userModel.findByEmail(email);
            if (!user || user.role !== 'customer') {
                return res.status(401).json({ error: 'Invalid email or password' });
            }
            const match = await bcrypt.compare(password, user.password_hash);
            if (!match) {
                return res.status(401).json({ error: 'Invalid email or password' });
            }
            const token = jwt.sign(
                { user_id: user.user_id, username: user.username, email: user.email, role: user.role },
                JWT_SECRET,
                { expiresIn: '7d' }
            );
            res.json({
                token,
                user: {
                    user_id: user.user_id,
                    username: user.username,
                    email: user.email,
                    phone: user.phone,
                    role: user.role,
                    avatar_url: user.avatar_url
                }
            });
        } catch (err) {
            console.error('API login error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    register: async (req, res) => {
        try {
            const { username, email, password, confirmPassword, phone } = req.body;
            if (!username || !email || !password) {
                return res.status(400).json({ error: 'All fields are required' });
            }
            if (password !== confirmPassword) {
                return res.status(400).json({ error: 'Passwords do not match' });
            }
            const existing = await userModel.findByEmail(email);
            if (existing) {
                return res.status(400).json({ error: 'Email already registered' });
            }
            const passwordHash = await bcrypt.hash(password, 10);
            const userId = await userModel.create(username, email, passwordHash, phone);
            const token = jwt.sign(
                { user_id: userId, username, email, role: 'customer' },
                JWT_SECRET,
                { expiresIn: '7d' }
            );
            res.status(201).json({
                token,
                user: {
                    user_id: userId,
                    username,
                    email,
                    phone: phone || null,
                    role: 'customer'
                }
            });
        } catch (err) {
            console.error('API register error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    adminLogin: async (req, res) => {
        try {
            const { email, password } = req.body;
            if (!email || !password) {
                return res.status(400).json({ error: 'Email and password are required' });
            }
            const user = await userModel.findByEmail(email);
            if (!user || user.role !== 'admin') {
                return res.status(401).json({ error: 'Invalid admin credentials' });
            }
            const match = await bcrypt.compare(password, user.password_hash);
            if (!match) {
                return res.status(401).json({ error: 'Invalid admin credentials' });
            }
            const token = jwt.sign(
                { user_id: user.user_id, username: user.username, email: user.email, role: user.role },
                JWT_SECRET,
                { expiresIn: '7d' }
            );
            res.json({
                token,
                user: {
                    user_id: user.user_id,
                    username: user.username,
                    email: user.email,
                    phone: user.phone,
                    role: user.role,
                    avatar_url: user.avatar_url
                }
            });
        } catch (err) {
            console.error('API adminLogin error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    getMe: async (req, res) => {
        try {
            const user = await userModel.findById(req.user.user_id);
            if (!user) return res.status(404).json({ error: 'User not found' });
            let cartCount = 0;
            let wishlistCount = 0;
            if (user.role === 'customer') {
                cartCount = await cartModel.getCount(user.user_id);
                wishlistCount = await wishlistModel.getCount(user.user_id);
            }
            res.json({
                user_id: user.user_id,
                username: user.username,
                email: user.email,
                phone: user.phone,
                role: user.role,
                avatar_url: user.avatar_url,
                cartCount,
                wishlistCount
            });
        } catch (err) {
            console.error('API getMe error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    // ===== PRODUCTS =====
    getProducts: async (req, res) => {
        try {
            const { category_id, featured } = req.query;
            let products;
            if (category_id) {
                products = await productModel.getByCategory(category_id);
            } else {
                products = await productModel.getAll();
            }
            if (featured === 'true') {
                products = products.slice(0, 9);
            }
            res.json(products.map(parseJsonFields));
        } catch (err) {
            console.error('API getProducts error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    getProductById: async (req, res) => {
        try {
            const product = await productModel.getById(req.params.id);
            if (!product) return res.status(404).json({ error: 'Product not found' });
            let isWishlisted = false;
            if (req.user) {
                isWishlisted = await wishlistModel.isWishlisted(req.user.user_id, product.product_id);
            }
            res.json({
                product: parseJsonFields(product),
                isWishlisted
            });
        } catch (err) {
            console.error('API getProductById error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    searchProducts: async (req, res) => {
        try {
            const query = req.query.q || '';
            const products = query ? await productModel.search(query) : [];
            res.json({
                query,
                products: products.map(parseJsonFields)
            });
        } catch (err) {
            console.error('API searchProducts error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    // ===== CATEGORIES =====
    getCategories: async (req, res) => {
        try {
            const categories = await categoryModel.getVisible();
            res.json(categories);
        } catch (err) {
            console.error('API getCategories error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    getCategoryBySlug: async (req, res) => {
        try {
            const categorySlug = req.params.slug.toLowerCase();
            const categoryName = CATEGORY_MAP[categorySlug];
            if (!categoryName) {
                return res.status(404).json({ error: 'Category not found' });
            }
            const [categoriesDB] = await pool.query(
                'SELECT * FROM categories WHERE category_name = ? AND is_visible = TRUE',
                [categoryName]
            );
            if (categoriesDB.length === 0) {
                return res.status(404).json({ error: 'Category not found' });
            }
            const category = categoriesDB[0];
            const products = await productModel.getByCategory(category.category_id);
            res.json({
                category,
                products: products.map(parseJsonFields)
            });
        } catch (err) {
            console.error('API getCategoryBySlug error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    // ===== CART =====
    getCart: async (req, res) => {
        try {
            const cartItems = await cartModel.getByUser(req.user.user_id);
            cartItems.forEach(item => {
                if (typeof item.product_images === 'string') {
                    try { item.product_images = JSON.parse(item.product_images); } catch (_) { item.product_images = []; }
                }
                if (typeof item.selected_attributes === 'string') {
                    try { item.selected_attributes = JSON.parse(item.selected_attributes); } catch (_) { item.selected_attributes = {}; }
                }
            });
            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            const cartCount = await cartModel.getCount(req.user.user_id);
            res.json({ cartItems, total, cartCount });
        } catch (err) {
            console.error('API getCart error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    addToCart: async (req, res) => {
        try {
            const { product_id, quantity, attributes } = req.body;
            const parsedAttributes = attributes ? (typeof attributes === 'string' ? JSON.parse(attributes) : attributes) : {};
            await cartModel.addItem(req.user.user_id, product_id, parseInt(quantity) || 1, parsedAttributes);
            const cartCount = await cartModel.getCount(req.user.user_id);
            res.json({ success: true, cartCount });
        } catch (err) {
            console.error('API addToCart error:', err);
            res.status(500).json({ error: 'Failed to add item to cart' });
        }
    },

    updateCartItem: async (req, res) => {
        try {
            const cartItemId = req.params.id;
            const { quantity } = req.body;
            await cartModel.updateQuantity(cartItemId, req.user.user_id, parseInt(quantity));
            const cartCount = await cartModel.getCount(req.user.user_id);
            res.json({ success: true, cartCount });
        } catch (err) {
            console.error('API updateCartItem error:', err);
            res.status(500).json({ error: 'Failed to update item' });
        }
    },

    removeCartItem: async (req, res) => {
        try {
            const cartItemId = req.params.id;
            await cartModel.removeItem(cartItemId, req.user.user_id);
            const cartCount = await cartModel.getCount(req.user.user_id);
            res.json({ success: true, cartCount });
        } catch (err) {
            console.error('API removeCartItem error:', err);
            res.status(500).json({ error: 'Failed to remove item' });
        }
    },

    clearCart: async (req, res) => {
        try {
            await cartModel.clearCart(req.user.user_id);
            res.json({ success: true, cartCount: 0 });
        } catch (err) {
            console.error('API clearCart error:', err);
            res.status(500).json({ error: 'Failed to clear cart' });
        }
    },

    // ===== WISHLIST =====
    getWishlist: async (req, res) => {
        try {
            const items = await wishlistModel.getByUser(req.user.user_id);
            items.forEach(item => {
                if (typeof item.product_images === 'string') {
                    try { item.product_images = JSON.parse(item.product_images); } catch (_) { item.product_images = []; }
                }
                if (typeof item.product_attributes === 'string') {
                    try { item.product_attributes = JSON.parse(item.product_attributes); } catch (_) { item.product_attributes = {}; }
                }
            });
            const wishlistCount = await wishlistModel.getCount(req.user.user_id);
            res.json({ wishlistItems: items, wishlistCount });
        } catch (err) {
            console.error('API getWishlist error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    toggleWishlist: async (req, res) => {
        try {
            const { product_id } = req.body;
            const added = await wishlistModel.toggle(req.user.user_id, product_id);
            const wishlistCount = await wishlistModel.getCount(req.user.user_id);
            res.json({ success: true, added, wishlistCount });
        } catch (err) {
            console.error('API toggleWishlist error:', err);
            res.status(500).json({ error: 'Failed to update wishlist' });
        }
    },

    // ===== ADDRESSES =====
    getAddresses: async (req, res) => {
        try {
            const addresses = await addressModel.getByUser(req.user.user_id);
            res.json(addresses);
        } catch (err) {
            console.error('API getAddresses error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    addAddress: async (req, res) => {
        try {
            const addressId = await addressModel.create(req.user.user_id, req.body);
            res.status(201).json({ success: true, address_id: addressId });
        } catch (err) {
            console.error('API addAddress error:', err);
            res.status(500).json({ error: 'Failed to add address' });
        }
    },

    updateAddress: async (req, res) => {
        try {
            await addressModel.update(req.params.id, req.user.user_id, req.body);
            res.json({ success: true });
        } catch (err) {
            console.error('API updateAddress error:', err);
            res.status(500).json({ error: 'Failed to update address' });
        }
    },

    deleteAddress: async (req, res) => {
        try {
            await addressModel.delete(req.params.id, req.user.user_id);
            res.json({ success: true });
        } catch (err) {
            console.error('API deleteAddress error:', err);
            res.status(500).json({ error: 'Failed to delete address' });
        }
    },

    setDefaultAddress: async (req, res) => {
        try {
            await addressModel.setDefault(req.params.id, req.user.user_id);
            res.json({ success: true });
        } catch (err) {
            console.error('API setDefaultAddress error:', err);
            res.status(500).json({ error: 'Failed to set default address' });
        }
    },

    // ===== ORDERS & CHECKOUT =====
    getCheckoutData: async (req, res) => {
        try {
            const userId = req.user.user_id;
            const cartItems = await cartModel.getByUser(userId);
            cartItems.forEach(item => {
                if (typeof item.product_images === 'string') {
                    try { item.product_images = JSON.parse(item.product_images); } catch (_) { item.product_images = []; }
                }
                if (typeof item.selected_attributes === 'string') {
                    try { item.selected_attributes = JSON.parse(item.selected_attributes); } catch (_) { item.selected_attributes = {}; }
                }
            });
            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            const addresses = await addressModel.getByUser(userId);
            res.json({ cartItems, total, addresses });
        } catch (err) {
            console.error('API getCheckoutData error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    createOrder: async (req, res) => {
        try {
            const { address_id, payment_method } = req.body;
            const userId = req.user.user_id;
            const cartItems = await cartModel.getByUser(userId);
            if (cartItems.length === 0) {
                return res.status(400).json({ error: 'Cart is empty' });
            }
            const total = cartItems.reduce((sum, item) => sum + item.product_price * item.quantity, 0);
            const orderItems = cartItems.map(item => ({
                product_id: item.product_id,
                quantity: item.quantity,
                unit_price: item.product_price,
                selected_attributes: typeof item.selected_attributes === 'string' ? JSON.parse(item.selected_attributes) : item.selected_attributes
            }));
            const orderId = await orderModel.create(userId, address_id || null, total, payment_method || 'credit_card', orderItems);
            await cartModel.clearCart(userId);
            res.status(201).json({ success: true, order_id: orderId });
        } catch (err) {
            console.error('API createOrder error:', err);
            res.status(500).json({ error: 'Failed to place order' });
        }
    },

    getUserOrders: async (req, res) => {
        try {
            const orders = await orderModel.getByUser(req.user.user_id);
            for (const order of orders) {
                const fullOrder = await orderModel.getById(order.order_id);
                order.items = fullOrder ? fullOrder.items : [];
                if (order.items) {
                    order.items.forEach(it => {
                        if (typeof it.product_images === 'string') {
                            try { it.product_images = JSON.parse(it.product_images); } catch (_) { it.product_images = []; }
                        }
                    });
                }
            }
            res.json(orders);
        } catch (err) {
            console.error('API getUserOrders error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    getOrderById: async (req, res) => {
        try {
            const order = await orderModel.getById(req.params.id);
            if (!order) return res.status(404).json({ error: 'Order not found' });
            if (req.user.role !== 'admin' && order.user_id !== req.user.user_id) {
                return res.status(403).json({ error: 'Unauthorized to view this order' });
            }
            if (order.items) {
                order.items.forEach(it => {
                    if (typeof it.product_images === 'string') {
                        try { it.product_images = JSON.parse(it.product_images); } catch (_) { it.product_images = []; }
                    }
                });
            }
            res.json(order);
        } catch (err) {
            console.error('API getOrderById error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    // ===== PROFILE =====
    getProfile: async (req, res) => {
        try {
            const user = await userModel.findById(req.user.user_id);
            if (!user) return res.status(404).json({ error: 'User not found' });
            res.json({
                user_id: user.user_id,
                username: user.username,
                email: user.email,
                phone: user.phone,
                role: user.role,
                avatar_url: user.avatar_url
            });
        } catch (err) {
            console.error('API getProfile error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    updateProfile: async (req, res) => {
        try {
            const { username, email, phone } = req.body;
            await userModel.updateProfile(req.user.user_id, username, email, phone);
            const user = await userModel.findById(req.user.user_id);
            res.json({
                success: true,
                user: {
                    user_id: user.user_id,
                    username: user.username,
                    email: user.email,
                    phone: user.phone,
                    role: user.role
                }
            });
        } catch (err) {
            console.error('API updateProfile error:', err);
            res.status(500).json({ error: 'Failed to update profile' });
        }
    },

    changePassword: async (req, res) => {
        try {
            const { currentPassword, newPassword, confirmPassword } = req.body;
            const user = await userModel.findById(req.user.user_id);
            const match = await bcrypt.compare(currentPassword, user.password_hash);
            if (!match) {
                return res.status(400).json({ error: 'Current password is incorrect' });
            }
            if (newPassword !== confirmPassword) {
                return res.status(400).json({ error: 'New passwords do not match' });
            }
            const passwordHash = await bcrypt.hash(newPassword, 10);
            await userModel.updatePassword(req.user.user_id, passwordHash);
            res.json({ success: true, message: 'Password changed successfully' });
        } catch (err) {
            console.error('API changePassword error:', err);
            res.status(500).json({ error: 'Failed to change password' });
        }
    },

    // ===== FAQS =====
    getFaqs: async (req, res) => {
        try {
            const [faqs] = await pool.query('SELECT * FROM faqs ORDER BY sort_order ASC');
            res.json(faqs);
        } catch (err) {
            console.error('API getFaqs error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    // ===== ADMIN / BACKOFFICE =====
    getAdminDashboard: async (req, res) => {
        try {
            const stats = await orderModel.getDashboardStats();
            res.json(stats);
        } catch (err) {
            console.error('API getAdminDashboard error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    getAdminCategories: async (req, res) => {
        try {
            const categories = await categoryModel.getAll();
            res.json(categories);
        } catch (err) {
            console.error('API getAdminCategories error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    addCategory: async (req, res) => {
        try {
            const { category_name, category_image } = req.body;
            const id = await categoryModel.create(category_name, category_image);
            res.status(201).json({ success: true, category_id: id });
        } catch (err) {
            console.error('API addCategory error:', err);
            res.status(500).json({ error: 'Failed to create category' });
        }
    },

    updateCategory: async (req, res) => {
        try {
            const { category_name, category_image } = req.body;
            await categoryModel.update(req.params.id, category_name, category_image);
            res.json({ success: true });
        } catch (err) {
            console.error('API updateCategory error:', err);
            res.status(500).json({ error: 'Failed to update category' });
        }
    },

    deleteCategory: async (req, res) => {
        try {
            await categoryModel.delete(req.params.id);
            res.json({ success: true });
        } catch (err) {
            console.error('API deleteCategory error:', err);
            res.status(500).json({ error: 'Failed to delete category' });
        }
    },

    toggleCategoryVisibility: async (req, res) => {
        try {
            await categoryModel.toggleVisibility(req.params.id);
            res.json({ success: true });
        } catch (err) {
            console.error('API toggleCategoryVisibility error:', err);
            res.status(500).json({ error: 'Failed to toggle category' });
        }
    },

    getAdminProducts: async (req, res) => {
        try {
            const { category_id } = req.query;
            let products;
            if (category_id) {
                products = await productModel.getByCategoryAdmin(category_id);
            } else {
                products = await productModel.getAllAdmin();
            }
            res.json(products.map(parseJsonFields));
        } catch (err) {
            console.error('API getAdminProducts error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    addProduct: async (req, res) => {
        try {
            const { category_id, product_name, author, product_description, product_price, product_attributes } = req.body;
            const images = req.files ? req.files.map(f => f.filename) : (req.body.product_images || []);
            const id = await productModel.create({
                category_id,
                product_name,
                author,
                product_description,
                product_price,
                product_images: images,
                product_attributes: product_attributes ? (typeof product_attributes === 'string' ? JSON.parse(product_attributes) : product_attributes) : {}
            });
            res.status(201).json({ success: true, product_id: id });
        } catch (err) {
            console.error('API addProduct error:', err);
            res.status(500).json({ error: 'Failed to add product' });
        }
    },

    updateProduct: async (req, res) => {
        try {
            const { category_id, product_name, author, product_description, product_price, product_attributes, existing_images } = req.body;
            const newImages = req.files ? req.files.map(f => f.filename) : [];
            let existing = existing_images ? (Array.isArray(existing_images) ? existing_images : [existing_images]) : [];
            if (typeof existing_images === 'string' && existing_images.startsWith('[')) {
                try { existing = JSON.parse(existing_images); } catch (_) {}
            }
            const allImages = [...existing, ...newImages];
            await productModel.update(req.params.id, {
                category_id,
                product_name,
                author,
                product_description,
                product_price,
                product_images: allImages,
                product_attributes: product_attributes ? (typeof product_attributes === 'string' ? JSON.parse(product_attributes) : product_attributes) : {}
            });
            res.json({ success: true });
        } catch (err) {
            console.error('API updateProduct error:', err);
            res.status(500).json({ error: 'Failed to update product' });
        }
    },

    deleteProduct: async (req, res) => {
        try {
            await productModel.delete(req.params.id);
            res.json({ success: true });
        } catch (err) {
            console.error('API deleteProduct error:', err);
            res.status(500).json({ error: 'Failed to delete product' });
        }
    },

    getAdminCustomers: async (req, res) => {
        try {
            const customers = await userModel.getAll();
            res.json(customers);
        } catch (err) {
            console.error('API getAdminCustomers error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    getAdminOrders: async (req, res) => {
        try {
            let orders = await orderModel.getAll();
            const { status } = req.query;
            if (status) {
                orders = orders.filter(o => o.status === status);
            }
            for (const order of orders) {
                const fullOrder = await orderModel.getById(order.order_id);
                order.items = fullOrder ? fullOrder.items : [];
                order.address = fullOrder ? fullOrder.address : null;
                if (order.items) {
                    order.items.forEach(it => {
                        if (typeof it.product_images === 'string') {
                            try { it.product_images = JSON.parse(it.product_images); } catch (_) { it.product_images = []; }
                        }
                    });
                }
            }
            res.json(orders);
        } catch (err) {
            console.error('API getAdminOrders error:', err);
            res.status(500).json({ error: 'Internal server error' });
        }
    },

    updateOrderStatus: async (req, res) => {
        try {
            const { status } = req.body;
            await orderModel.updateStatus(req.params.id, status);
            res.json({ success: true });
        } catch (err) {
            console.error('API updateOrderStatus error:', err);
            res.status(500).json({ error: 'Failed to update order status' });
        }
    }
};
