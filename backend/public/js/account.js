/* ===========================================
   Account Pages — Address Book, Order History, Profile
   Server-side rendered — minimal JS for interactions
   =========================================== */
$(document).ready(function () {

    // ========================================================================
    // Address Book — Delete confirmation
    // ========================================================================
    $('.btn-delete-address').on('click', function (e) {
        if (!confirm('Are you sure you want to delete this address?')) {
            e.preventDefault();
        }
    });

    // ========================================================================
    // Order History — Toggle order details (Bootstrap collapse handles this)
    // ========================================================================
    // No custom JS needed — Bootstrap data-bs-toggle="collapse" handles expand/collapse

    // ========================================================================
    // Profile Page — Client-side validation before form submit
    // ========================================================================
    $('#password-form').on('submit', function (e) {
        const newPass = $(this).find('input[name="newPassword"]').val();
        const confirmPass = $(this).find('input[name="confirmPassword"]').val();
        if (newPass && newPass !== confirmPass) {
            e.preventDefault();
            if (window.showToast) showToast('New passwords do not match', 'error');
        }
    });
});
