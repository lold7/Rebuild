const express = require('express');
const router = express.Router();
const webstoreController = require('../controllers/webstoreController');

router.get('/', webstoreController.home);
router.get('/categories', webstoreController.allCategories);
router.get('/products', webstoreController.allProducts);
router.get('/category/fiction', webstoreController.categoryFiction);
router.get('/category/children', webstoreController.categoryChildren);
router.get('/category/nonfiction', webstoreController.categoryNonfiction);
router.get('/category/mystery', webstoreController.categoryMystery);
router.get('/category/science', webstoreController.categoryScience);
router.get('/product/:id', webstoreController.productDetail);
router.get('/search', webstoreController.search);
router.get('/contact', webstoreController.contact);
router.get('/condition', webstoreController.condition);
router.get('/faq', webstoreController.faq);

module.exports = router;
