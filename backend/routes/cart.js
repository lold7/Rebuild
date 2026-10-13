const express = require('express');
const router = express.Router();
const cartController = require('../controllers/cartController');
const authCustomer = require('../middleware/authCustomer');

// This router is mounted at both /cart and /checkout in app.js
// Use req.baseUrl to differentiate the GET / handler
router.get('/', authCustomer, (req, res, next) => {
    if (req.baseUrl === '/checkout') {
        return cartController.checkout(req, res, next);
    }
    return cartController.viewCart(req, res, next);
});

// Cart routes
router.post('/add', authCustomer, cartController.addItem);
router.post('/update', authCustomer, cartController.updateItem);
router.post('/remove', authCustomer, cartController.removeItem);

// Checkout routes
router.get('/payment', authCustomer, cartController.choosePayment);
router.post('/confirm', authCustomer, cartController.confirmOrder);

module.exports = router;
