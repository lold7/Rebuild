/* ===========================================
   Form Validation — Login, Register, Profile forms
   =========================================== */
$(document).ready(function () {

    // ========================================================================
    // Login Page (webstore/login.ejs)
    // ========================================================================
    const $loginForm = $('#login-form');
    if ($loginForm.length) {
        $loginForm.on('submit', function (e) {
            const email = $('#login-email').val().trim();
            const password = $('#login-password').val().trim();
            let errors = [];

            if (!email) errors.push('Email is required');
            else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.push('Invalid email format');
            if (!password) errors.push('Password is required');
            else if (password.length < 6) errors.push('Password must be at least 6 characters');

            if (errors.length > 0) {
                e.preventDefault(); // ระงับการส่งฟอร์มเฉพาะตอนที่มี Error
                showFormError($loginForm, errors.join('<br>'));
            }
            // ถ้าไม่มี Error จะไม่โดน e.preventDefault() 
            // ฟอร์มจะถูก Submit ไปที่ action="/auth/login" (Backend) โดยอัตโนมัติ
        });
    }

    // ========================================================================
    // Register Page (webstore/register.ejs)
    // ========================================================================
    const $registerForm = $('#register-form'); // ตรวจสอบว่าในไฟล์ HTML/EJS คุณตั้ง id="register-form" ไว้หรือเปล่านะครับ
    if ($registerForm.length) {
        $registerForm.on('submit', function (e) {
            // ดึงค่าตาม id ที่คุณตั้งไว้ในฟอร์ม
            const email = $('#reg-email').val()?.trim() || $('input[name="email"]').val()?.trim();
            const password = $('#reg-password').val()?.trim() || $('input[name="password"]').val()?.trim();
            let errors = [];

            if (!email) errors.push('Email is required');
            else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.push('Invalid email format');
            if (!password) errors.push('Password is required');
            else if (password.length < 6) errors.push('Password must be at least 6 characters');

            if (errors.length > 0) {
                e.preventDefault(); // ระงับการส่งฟอร์มเฉพาะตอนที่มี Error
                showFormError($registerForm, errors.join('<br>'));
            }
            // ถ้าไม่มี Error ฟอร์มจะถูก Submit ไปที่ action="/auth/register"
        });
    }

    // ========================================================================
    // Helper: Show form error
    // ========================================================================
    function showFormError($form, message) {
        let $error = $form.find('.form-error-alert');
        if ($error.length === 0) {
            $form.prepend('<div class="alert alert-danger form-error-alert mb-3"></div>');
            $error = $form.find('.form-error-alert');
        }
        $error.html(message).show();
        setTimeout(function () {
            $error.fadeOut();
        }, 5000);
    }
});