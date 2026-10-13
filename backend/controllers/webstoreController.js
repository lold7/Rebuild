const productModel = require('../models/productModel');
const categoryModel = require('../models/categoryModel');
const wishlistModel = require('../models/wishlistModel');
const pool = require('../config/db');

// เปลี่ยนมาแมปกับ "ชื่อหมวดหมู่" ใน Database แทนการใช้ ID ที่อาจคลาดเคลื่อนได้
const CATEGORY_MAP = {
    fiction: 'Fiction',
    children: 'Children',
    nonfiction: 'Non-Fiction',
    mystery: 'Mystery & Thriller',
    science: 'Science & Technology'
};

// Helper to render a category page
const renderCategory = async (req, res, categorySlug, template) => {
    try {
        const categoryName = CATEGORY_MAP[categorySlug];
        
        // 1. หาหมวดหมู่จาก "ชื่อ" แทน ID
        const [categoriesDB] = await pool.query(
            'SELECT * FROM categories WHERE category_name = ? AND is_visible = TRUE', 
            [categoryName]
        );

        if (categoriesDB.length === 0) {
            return res.status(404).send('Category not found');
        }

        const category = categoriesDB[0];
        
        // 2. ดึงสินค้าด้วย category_id ที่ได้มาจากฐานข้อมูลจริงๆ
        const products = await productModel.getByCategory(category.category_id);
        const categories = await categoryModel.getVisible();
        
        res.render(`webstore/${template}`, { category, products, categories });
    } catch (err) {
        console.error(err);
        res.status(500).send('Something went wrong');
    }
};

module.exports = {
    home: async (req, res) => {
        try {
            const products = await productModel.getAll();
            const categories = await categoryModel.getVisible();
            res.render('webstore/home', { products, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    allCategories: async (req, res) => {
        try {
            const categories = await categoryModel.getVisible();
            res.render('webstore/allCategories', { categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    allProducts: async (req, res) => {
        try {
            const products = await productModel.getAll();
            const categories = await categoryModel.getVisible();
            res.render('webstore/allProducts', { products, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    categoryFiction: (req, res) => renderCategory(req, res, 'fiction', 'category-fiction'),
    categoryChildren: (req, res) => renderCategory(req, res, 'children', 'category-children'),
    categoryNonfiction: (req, res) => renderCategory(req, res, 'nonfiction', 'category-nonfiction'),
    categoryMystery: (req, res) => renderCategory(req, res, 'mystery', 'category-mystery'),
    categoryScience: (req, res) => renderCategory(req, res, 'science', 'category-science'),

    productDetail: async (req, res) => {
        try {
            const product = await productModel.getById(req.params.id);
            if (!product) {
                return res.status(404).send('Product not found');
            }
            const categories = await categoryModel.getVisible();
            let isWishlisted = false;
            if (req.session.user) {
                isWishlisted = await wishlistModel.isWishlisted(req.session.user.user_id, product.product_id);
            }
            res.render('webstore/productDetail', { product, categories, isWishlisted });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    search: async (req, res) => {
        try {
            const query = req.query.q || '';
            const products = query ? await productModel.search(query) : [];
            const categories = await categoryModel.getVisible();
            res.render('webstore/search-results', { products, categories, query });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    contact: async (req, res) => {
        try {
            const categories = await categoryModel.getVisible();
            res.render('webstore/contact', { categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    condition: async (req, res) => {
        try {
            const categories = await categoryModel.getVisible();
            res.render('webstore/condition', { categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    },

    faq: async (req, res) => {
        try {
            const [faqs] = await pool.query('SELECT * FROM faqs ORDER BY sort_order ASC');
            const categories = await categoryModel.getVisible();
            res.render('webstore/faq', { faqs, categories });
        } catch (err) {
            console.error(err);
            res.status(500).send('Something went wrong');
        }
    }
};