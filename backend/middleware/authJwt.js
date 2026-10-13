const jwt = require('jsonwebtoken');

const verifyToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    if (!authHeader) {
        return res.status(401).json({ error: 'No token provided' });
    }
    const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : authHeader;
    try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET || 'inwza-jwt-secret-key-2026');
        req.user = decoded;
        next();
    } catch (err) {
        return res.status(401).json({ error: 'Invalid or expired token' });
    }
};

const optionalToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    if (authHeader) {
        const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : authHeader;
        try {
            const decoded = jwt.verify(token, process.env.JWT_SECRET || 'inwza-jwt-secret-key-2026');
            req.user = decoded;
        } catch (err) {
            // ignore invalid token for optional auth
        }
    }
    next();
};

const verifyAdmin = (req, res, next) => {
    verifyToken(req, res, () => {
        if (req.user && req.user.role === 'admin') {
            next();
        } else {
            return res.status(403).json({ error: 'Access denied: Admin role required' });
        }
    });
};

module.exports = { verifyToken, optionalToken, verifyAdmin };
