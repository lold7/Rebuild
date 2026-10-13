require('dotenv').config();
const express = require('express');
const session = require('express-session');
const path = require('path');
const cors = require('cors');

const app = express();

// CORS configuration for Angular SPA
app.use(cors({
    origin: ['http://localhost:4200', 'http://127.0.0.1:4200'],
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With']
}));

// Body parser
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

// View engine (kept for fallback)
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Static files (including images and uploads)
app.use(express.static(path.join(__dirname, 'public')));
app.use('/images', express.static(path.join(__dirname, 'public/images')));

// Session
app.use(session({
    secret: process.env.SESSION_SECRET || 'default-secret',
    resave: false,
    saveUninitialized: false,
    cookie: {
        httpOnly: true,
        secure: false,
        maxAge: 24 * 60 * 60 * 1000 // 24 hours
    }
}));

// Global middleware for views
const cartModel = require('./models/cartModel');
const wishlistModel = require('./models/wishlistModel');

app.use(async (req, res, next) => {
    res.locals.user = req.session.user || null;
    res.locals.cartCount = 0;
    res.locals.wishlistCount = 0;

    if (req.session.user) {
        try {
            res.locals.cartCount = await cartModel.getCount(req.session.user.user_id);
            res.locals.wishlistCount = await wishlistModel.getCount(req.session.user.user_id);
        } catch (err) {
            // Silently fail
        }
    }
    next();
});

// REST API Routes
const apiRoutes = require('./routes/api');
app.use('/api', apiRoutes);

// Original EJS routes
const webstoreRoutes = require('./routes/webstore');
const authRoutes = require('./routes/auth');
const cartRoutes = require('./routes/cart');
const accountRoutes = require('./routes/account');
const backofficeRoutes = require('./routes/backoffice');

app.use('/auth', authRoutes);
app.use('/cart', cartRoutes);
app.use('/checkout', cartRoutes);
app.use('/account', accountRoutes);
app.use('/backoffice', backofficeRoutes);
app.use('/', webstoreRoutes);

// 404 handler
app.use((req, res) => {
    if (req.path.startsWith('/api')) {
        return res.status(404).json({ error: 'API route not found' });
    }
    res.status(404).send('Page not found');
});

// Error handler
app.use((err, req, res, next) => {
    console.error('Server error:', err);
    if (req.path.startsWith('/api')) {
        return res.status(500).json({ error: 'Internal server error', details: err.message });
    }
    res.status(500).send('Something went wrong');
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Server running on http://localhost:${PORT}`);
    console.log(`REST API mounted at http://localhost:${PORT}/api`);
});
