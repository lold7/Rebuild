const express = require('express');
const router = express.Router();
const backofficeController = require('../controllers/backofficeController');
const authController = require('../controllers/authController');
const authAdmin = require('../middleware/authAdmin');
const multer = require('multer');
const path = require('path');

// Multer config for product image uploads
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

// Admin login
router.get('/login', authController.adminLoginPage);
router.post('/login', authController.adminLogin);
router.get('/logout', authController.adminLogout);

// Protected admin routes
router.get('/', authAdmin, backofficeController.dashboard);
router.get('/categories', authAdmin, backofficeController.categories);
router.post('/categories/add', authAdmin, backofficeController.addCategory);
router.post('/categories/edit/:id', authAdmin, backofficeController.editCategory);
router.post('/categories/delete/:id', authAdmin, backofficeController.deleteCategory);
router.post('/categories/toggle/:id', authAdmin, backofficeController.toggleCategory);

router.get('/products', authAdmin, backofficeController.products);
router.get('/inventory', authAdmin, backofficeController.inventory);
router.get('/inventory/:id', authAdmin, backofficeController.inventoryEdit);
router.post('/inventory/add', authAdmin, upload.array('images', 5), backofficeController.addProduct);
router.post('/inventory/edit/:id', authAdmin, upload.array('images', 5), backofficeController.editProduct);
router.post('/products/delete/:id', authAdmin, backofficeController.deleteProduct);

router.get('/customers', authAdmin, backofficeController.customers);
router.get('/orders', authAdmin, backofficeController.orders);
router.post('/orders/status/:id', authAdmin, backofficeController.updateOrderStatus);
router.get('/profile', authAdmin, backofficeController.adminProfile);
router.post('/profile', authAdmin, backofficeController.updateAdminProfile);
router.post('/profile/password', authAdmin, backofficeController.changeAdminPassword);

module.exports = router;
