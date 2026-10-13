/* ===========================================
   main.js — Global functionality + Homepage interactivity
   =========================================== */

$(document).ready(function () {

    // ========================================================================
    // 1. Search Bar — redirect to /search?q=xxx
    // ========================================================================
    $('form').each(function () {
        const $form = $(this);
        const $searchInput = $form.find('input[type="search"]');
        if ($searchInput.length && !$form.attr('action')) {
            $form.on('submit', function (e) {
                e.preventDefault();
                const query = $searchInput.val().trim();
                if (query) {
                    window.location.href = '/search?q=' + encodeURIComponent(query);
                }
            });
        }
    });

    // ========================================================================
    // 2. Product Detail Page — Add to Cart (server-side via AJAX)
    // ========================================================================
    $('#btn-add-to-cart').on('click', function () {
        const productId = $(this).data('product-id') || $('input[name="product_id"]').val();
        if (!productId) return;

        const qty = parseInt($('#product-qty').val()) || 1;
        const format = $('#product-format').val() || 'paperback';
        const formatLabel = $('#product-format option:selected').text() || 'Paperback';

        $.ajax({
            url: '/cart/add',
            method: 'POST',
            data: {
                product_id: productId,
                quantity: qty,
                attributes: JSON.stringify({ format: formatLabel })
            },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    // Update cart badge
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
            error: function (xhr) {
                if (xhr.status === 401 || xhr.status === 302) {
                    window.location.href = '/auth/login?returnTo=' + encodeURIComponent(window.location.pathname);
                } else {
                    if (window.showToast) showToast('Failed to add to cart', 'error');
                }
            }
        });
    });

    // ========================================================================
    // 3. Product Detail Page — Wishlist toggle (server-side via AJAX)
    // ========================================================================
    $('#btn-wishlist').on('click', function () {
        const productId = $(this).data('product-id') || $('input[name="product_id"]').val();
        if (!productId) return;

        const $btn = $(this);
        const $icon = $btn.find('i');

        $.ajax({
            url: '/account/wishlist/toggle',
            method: 'POST',
            data: { product_id: productId },
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            success: function (res) {
                if (res.success) {
                    if (res.added) {
                        $icon.removeClass('bi-heart').addClass('bi-heart-fill text-danger');
                        if (window.showToast) showToast('Added to wishlist!');
                    } else {
                        $icon.removeClass('bi-heart-fill text-danger').addClass('bi-heart');
                        if (window.showToast) showToast('Removed from wishlist');
                    }
                    // Update wishlist badge
                    $('.wishlist-badge').each(function () {
                        if (res.wishlistCount > 0) {
                            $(this).text(res.wishlistCount).show();
                        } else {
                            $(this).hide();
                        }
                    });
                }
            },
            error: function (xhr) {
                if (xhr.status === 401 || xhr.status === 302) {
                    window.location.href = '/auth/login?returnTo=' + encodeURIComponent(window.location.pathname);
                } else {
                    if (window.showToast) showToast('Failed to update wishlist', 'error');
                }
            }
        });
    });

    // ========================================================================
    // 4. Make product cards clickable → /product/N
    // ========================================================================
    $(document).on('click', '.book-card-hover[data-product-id]', function () {
        const productId = $(this).data('product-id');
        if (productId) {
            window.location.href = '/product/' + productId;
        }
    });

    // ========================================================================
    // 5. Scroll-triggered fade-in animations (homepage)
    // ========================================================================
    if ($('.fade-in-on-scroll').length) {
        const observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    $(entry.target).addClass('visible');
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.1 });

        $('.fade-in-on-scroll').each(function () {
            observer.observe(this);
        });
    }

    // ========================================================================
    // 6. Product Detail — Quantity buttons
    // ========================================================================
    const $qtyInput = $('#product-qty');
    if ($qtyInput.length) {
        $qtyInput.closest('.input-group').find('button').first().off('click').on('click', function () {
            let val = parseInt($qtyInput.val()) || 1;
            if (val > 1) $qtyInput.val(val - 1);
        });
        $qtyInput.closest('.input-group').find('button').last().off('click').on('click', function () {
            let val = parseInt($qtyInput.val()) || 1;
            if (val < 10) $qtyInput.val(val + 1);
        });
    }

});
