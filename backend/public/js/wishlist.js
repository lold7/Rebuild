/* ===========================================
   Wishlist — Server-side wishlist via AJAX
   =========================================== */

$(document).ready(function () {

    // ========================================================================
    // Wishlist Page Interactions (only runs on wishlist page)
    // ========================================================================
    const $wishlistItems = $('#wishlist-items');
    if ($wishlistItems.length === 0) return;

    // Remove from wishlist
    $wishlistItems.on('click', '.btn-remove-wishlist', function (e) {
        e.stopPropagation();
        e.preventDefault();
        const $btn = $(this);
        const productId = $btn.data('product-id');
        const $card = $btn.closest('.col-lg-3, .col-md-4, .col-sm-6').length
            ? $btn.closest('.col-lg-3, .col-md-4, .col-sm-6')
            : $btn.closest('[class*="col-"]');

        $.ajax({
            url: '/account/wishlist/toggle',
            method: 'POST',
            data: { product_id: productId },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    $card.fadeOut(300, function () {
                        $(this).remove();
                        // Update stat number
                        const remaining = $wishlistItems.find('[data-product-id]').closest('[class*="col-"]').length;
                        $('.stat-number').first().text(remaining);
                        // Update wishlist badge
                        $('.wishlist-badge').each(function () {
                            if (res.wishlistCount > 0) {
                                $(this).text(res.wishlistCount).show();
                            } else {
                                $(this).hide();
                            }
                        });
                        // Show empty state if no items left
                        if (remaining === 0) {
                            $wishlistItems.html(
                                '<div class="text-center py-5">' +
                                '  <i class="bi bi-heart display-1 text-muted mb-3"></i>' +
                                '  <h3 class="fw-bold">Your wishlist is empty</h3>' +
                                '  <p class="text-muted">Browse our collection and save books you love.</p>' +
                                '  <a href="/products" class="btn btn-primary rounded-pill px-4 mt-3">Browse Books</a>' +
                                '</div>'
                            );
                        }
                    });
                    if (window.showToast) showToast('Removed from wishlist');
                }
            }
        });
    });

    // Add to cart from wishlist
    $wishlistItems.on('click', '.btn-add-to-cart-from-wishlist', function (e) {
        e.stopPropagation();
        const $btn = $(this);
        const productId = $btn.data('product-id');

        $.ajax({
            url: '/cart/add',
            method: 'POST',
            data: {
                product_id: productId,
                quantity: 1,
                attributes: JSON.stringify({ format: 'Paperback' })
            },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    $('.cart-badge').each(function () {
                        if (res.cartCount > 0) {
                            $(this).text(res.cartCount).show();
                        } else {
                            $(this).hide();
                        }
                    });
                    if (window.showToast) showToast('Added to cart!');
                }
            },
            error: function () {
                if (window.showToast) showToast('Failed to add to cart', 'error');
            }
        });
    });
});
