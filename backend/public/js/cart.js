/* ===========================================
   Cart — Server-side cart via AJAX
   =========================================== */

/* ===========================================
   Toast Notification Helper
   =========================================== */
function showToast(message, type) {
    type = type || 'success';
    let $container = $('#toast-container');
    if ($container.length === 0) {
        $('body').append('<div id="toast-container" class="toast-container position-fixed bottom-0 end-0 p-3" style="z-index:1100"></div>');
        $container = $('#toast-container');
    }
    const toastId = 'toast-' + Date.now();
    const bgClass = type === 'success' ? 'bg-success' : 'bg-danger';
    const html = '<div id="' + toastId + '" class="toast align-items-center text-white ' + bgClass + ' border-0" role="alert">' +
        '<div class="d-flex">' +
        '<div class="toast-body">' + message + '</div>' +
        '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>' +
        '</div></div>';
    $container.append(html);
    const toastEl = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastEl, { delay: 3000 });
    toast.show();
    $(toastEl).on('hidden.bs.toast', function () {
        $(this).remove();
    });
}

window.showToast = showToast;

/* ===========================================
   Cart Page Interactions (only runs on cart page)
   =========================================== */
$(document).ready(function () {
    const $cartContainer = $('#cart-items-container');
    if ($cartContainer.length === 0) return; // Not on cart page

    // Quantity plus
    $cartContainer.on('click', '.btn-plus', function () {
        const $item = $(this).closest('.cart-item');
        const cartItemId = $item.data('cart-item-id');
        const $qtyInput = $item.find('.item-qty');
        const newQty = parseInt($qtyInput.val()) + 1;

        $.ajax({
            url: '/cart/update',
            method: 'POST',
            data: { cart_item_id: cartItemId, quantity: newQty },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    $qtyInput.val(newQty);
                    // Update line total
                    const unitPrice = parseFloat($item.find('.item-price').text());
                    $item.find('.item-total').text((unitPrice * newQty).toFixed(2));
                    updateSubtotal();
                    updateCartBadge(res.cartCount);
                }
            }
        });
    });

    // Quantity minus
    $cartContainer.on('click', '.btn-minus', function () {
        const $item = $(this).closest('.cart-item');
        const cartItemId = $item.data('cart-item-id');
        const $qtyInput = $item.find('.item-qty');
        const currentQty = parseInt($qtyInput.val());

        if (currentQty <= 1) {
            // Remove item
            removeCartItem($item, cartItemId);
            return;
        }

        const newQty = currentQty - 1;
        $.ajax({
            url: '/cart/update',
            method: 'POST',
            data: { cart_item_id: cartItemId, quantity: newQty },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    $qtyInput.val(newQty);
                    const unitPrice = parseFloat($item.find('.item-price').text());
                    $item.find('.item-total').text((unitPrice * newQty).toFixed(2));
                    updateSubtotal();
                    updateCartBadge(res.cartCount);
                }
            }
        });
    });

    // Remove item
    $cartContainer.on('click', '.btn-remove', function (e) {
        e.preventDefault();
        const $item = $(this).closest('.cart-item');
        const cartItemId = $item.data('cart-item-id');
        removeCartItem($item, cartItemId);
    });

    function removeCartItem($item, cartItemId) {
        $.ajax({
            url: '/cart/remove',
            method: 'POST',
            data: { cart_item_id: cartItemId },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    $item.fadeOut(300, function () {
                        $(this).remove();
                        updateSubtotal();
                        updateCartBadge(res.cartCount);
                        // Check if cart is empty
                        if ($cartContainer.find('.cart-item').length === 0) {
                            $('#empty-cart-msg').removeClass('d-none');
                            $('#btn-checkout').addClass('disabled').text('Cart is empty');
                        }
                    });
                }
            }
        });
    }

    function updateSubtotal() {
        let total = 0;
        $cartContainer.find('.cart-item').each(function () {
            total += parseFloat($(this).find('.item-total').text()) || 0;
        });
        $('#cart-subtotal').text(total.toFixed(2));
    }

    function updateCartBadge(count) {
        $('.cart-badge').each(function () {
            if (count > 0) {
                $(this).text(count).show();
            } else {
                $(this).hide();
            }
        });
    }

    // Checkout button
    $('#btn-checkout').on('click', function (e) {
        e.preventDefault();
        if (!$(this).hasClass('disabled')) {
            window.location.href = '/checkout';
        }
    });
});
