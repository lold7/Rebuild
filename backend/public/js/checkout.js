/* ===========================================
   Checkout Flow — Server-side checkout & payment
   =========================================== */
$(document).ready(function () {

    // ========================================================================
    // Checkout Page — Address selection + Continue to Payment
    // ========================================================================
    $('#btn-continue-payment').on('click', function (e) {
        e.preventDefault();
        const selectedAddr = $('input[name="shipping-address"]:checked').val();
        if (!selectedAddr) {
            if (window.showToast) showToast('Please select a shipping address', 'error');
            return;
        }
        window.location.href = '/checkout/payment?address_id=' + selectedAddr;
    });

    // ========================================================================
    // Choose Payment Page — Payment method + Confirm Order
    // ========================================================================
    // Payment method tab selection
    $('.tab-btn').on('click', function () {
        $('.tab-btn').removeClass('active');
        $(this).addClass('active');
        // Update hidden input
        $('#payment-method-input').val($(this).data('method') || $(this).text().trim());
    });

    // Confirm order — submit form to server
    $('#btn-confirm-order').on('click', function (e) {
        e.preventDefault();
        const $form = $('#payment-form');
        if ($form.length) {
            $form.submit();
        }
    });
});
