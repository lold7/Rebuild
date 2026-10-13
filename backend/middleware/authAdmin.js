module.exports = (req, res, next) => {
    if (req.session && req.session.user && req.session.user.role === 'admin') {
        return next();
    }
    res.redirect('/backoffice/login');
};
