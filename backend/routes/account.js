const express = require('express');
const router = express.Router();
const accountController = require('../controllers/accountController');
const authCustomer = require('../middleware/authCustomer');

router.get('/profile', authCustomer, accountController.profile);
router.post('/profile', authCustomer, accountController.updateProfile);
router.post('/password', authCustomer, accountController.changePassword);
router.get('/orders', authCustomer, accountController.orderHistory);
router.get('/orders/:id', authCustomer, accountController.orderDetail);
router.get('/wishlist', authCustomer, accountController.wishlist);
router.post('/wishlist/toggle', authCustomer, accountController.toggleWishlist);
router.get('/addresses', authCustomer, accountController.addressBook);
router.post('/addresses/add', authCustomer, accountController.addAddress);
router.post('/addresses/edit/:id', authCustomer, accountController.editAddress);
router.post('/addresses/delete/:id', authCustomer, accountController.deleteAddress);
router.post('/addresses/default/:id', authCustomer, accountController.setDefaultAddress);

module.exports = router;
