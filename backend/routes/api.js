const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const apiController = require('../controllers/apiController');
const { verifyToken, optionalToken, verifyAdmin } = require('../middleware/authJwt');

// Multer config for file uploads
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, path.join(__dirname, '../public/images/products'));
    },
    filename: (req, file, cb) => {
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
        cb(null, uniqueSuffix + path.extname(file.originalname));
    }
});
const upload = multer({ storage });

// ===== Auth Routes =====
router.post('/auth/login', apiController.login);
router.post('/auth/register', apiController.register);
router.post('/auth/admin/login', apiController.adminLogin);
router.get('/auth/me', verifyToken, apiController.getMe);

// ===== Product Routes =====
router.get('/products', apiController.getProducts);
router.get('/products/search', apiController.searchProducts);
router.get('/products/:id', optionalToken, apiController.getProductById);

// ===== Category Routes =====
router.get('/categories', apiController.getCategories);
router.get('/categories/:slug/products', apiController.getCategoryBySlug);

// ===== Cart Routes =====
router.get('/cart', verifyToken, apiController.getCart);
router.post('/cart/add', verifyToken, apiController.addToCart);
router.put('/cart/:id', verifyToken, apiController.updateCartItem);
router.delete('/cart/:id', verifyToken, apiController.removeCartItem);
router.post('/cart/clear', verifyToken, apiController.clearCart);

// ===== Wishlist Routes =====
router.get('/wishlist', verifyToken, apiController.getWishlist);
router.post('/wishlist/toggle', verifyToken, apiController.toggleWishlist);

// ===== Address Book Routes =====
router.get('/account/addresses', verifyToken, apiController.getAddresses);
router.post('/account/addresses', verifyToken, apiController.addAddress);
router.put('/account/addresses/:id', verifyToken, apiController.updateAddress);
router.delete('/account/addresses/:id', verifyToken, apiController.deleteAddress);
router.put('/account/addresses/:id/default', verifyToken, apiController.setDefaultAddress);

// ===== Checkout & Orders Routes =====
router.get('/checkout', verifyToken, apiController.getCheckoutData);
router.post('/orders', verifyToken, apiController.createOrder);
router.get('/account/orders', verifyToken, apiController.getUserOrders);
router.get('/orders/:id', verifyToken, apiController.getOrderById);

// ===== Profile Routes =====
router.get('/account/profile', verifyToken, apiController.getProfile);
router.put('/account/profile', verifyToken, apiController.updateProfile);
router.put('/account/password', verifyToken, apiController.changePassword);

// ===== FAQ Routes =====
router.get('/faqs', apiController.getFaqs);

// ===== Admin / Backoffice Routes =====
router.get('/admin/dashboard', verifyAdmin, apiController.getAdminDashboard);
router.get('/admin/categories', verifyAdmin, apiController.getAdminCategories);
router.post('/admin/categories', verifyAdmin, apiController.addCategory);
router.put('/admin/categories/:id', verifyAdmin, apiController.updateCategory);
router.delete('/admin/categories/:id', verifyAdmin, apiController.deleteCategory);
router.put('/admin/categories/:id/toggle', verifyAdmin, apiController.toggleCategoryVisibility);

router.get('/admin/products', verifyAdmin, apiController.getAdminProducts);
router.post('/admin/products', verifyAdmin, upload.array('images', 5), apiController.addProduct);
router.put('/admin/products/:id', verifyAdmin, upload.array('images', 5), apiController.updateProduct);
router.delete('/admin/products/:id', verifyAdmin, apiController.deleteProduct);

router.get('/admin/customers', verifyAdmin, apiController.getAdminCustomers);
router.get('/admin/orders', verifyAdmin, apiController.getAdminOrders);
router.put('/admin/orders/:id/status', verifyAdmin, apiController.updateOrderStatus);

router.get('/admin/profile', verifyAdmin, apiController.getProfile);
router.put('/admin/profile', verifyAdmin, apiController.updateProfile);
router.put('/admin/profile/password', verifyAdmin, apiController.changePassword);

module.exports = router;
