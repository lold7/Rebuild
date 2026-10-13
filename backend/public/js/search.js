/* ===========================================
   Search Results — filter MOCK_PRODUCTS by query param
   =========================================== */
$(document).ready(function () {
    const $resultsContainer = $('#search-results-container');
    if ($resultsContainer.length === 0 || !window.MOCK_PRODUCTS) return;

    const params = new URLSearchParams(window.location.search);
    const query = params.get('q') || '';
    const imgBase = '../../public/images/products/';

    // Display search term
    $('#search-query-display').text(query || 'all books');

    if (!query) {
        $resultsContainer.html(
            '<div class="text-center py-5">' +
            '  <i class="bi bi-search display-1 text-muted mb-3"></i>' +
            '  <h3 class="fw-bold">Enter a search term</h3>' +
            '  <p class="text-muted">Use the search bar above to find books.</p>' +
            '</div>'
        );
        return;
    }

    // Filter products
    const lowerQuery = query.toLowerCase();
    const results = window.MOCK_PRODUCTS.filter(function (p) {
        return p.product_name.toLowerCase().indexOf(lowerQuery) > -1 ||
               p.author.toLowerCase().indexOf(lowerQuery) > -1 ||
               p.category_name.toLowerCase().indexOf(lowerQuery) > -1;
    });

    // Show result count
    $('#search-result-count').text(results.length + ' result' + (results.length !== 1 ? 's' : '') + ' found');

    if (results.length === 0) {
        $resultsContainer.html(
            '<div class="text-center py-5">' +
            '  <i class="bi bi-emoji-frown display-1 text-muted mb-3"></i>' +
            '  <h3 class="fw-bold">No results found for "' + $('<span>').text(query).html() + '"</h3>' +
            '  <p class="text-muted">Try searching with different keywords.</p>' +
            '  <a href="allProducts.html" class="btn btn-primary rounded-pill px-4 mt-3">Browse All Books</a>' +
            '</div>'
        );
        return;
    }

    // Render results
    let html = '<div class="row g-4">';
    $.each(results, function (i, product) {
        html +=
            '<div class="col-lg-3 col-md-4 col-sm-6 search-result-item">' +
            '  <div class="card border-0 h-100 bg-transparent text-center book-card-hover" style="cursor:pointer;" data-product-id="' + product.product_id + '">' +
            '    <img src="' + imgBase + product.product_images[0] + '" class="card-img-top rounded-4 shadow-sm mb-3" alt="' + product.product_name + '" style="height: 320px; object-fit: cover;">' +
            '    <h6 class="fw-bold mb-1">' + product.product_name + '</h6>' +
            '    <p class="text-muted small mb-1">' + product.author + '</p>' +
            '    <p class="fw-bold">$' + product.product_price.toFixed(2) + '</p>' +
            '  </div>' +
            '</div>';
    });
    html += '</div>';
    $resultsContainer.html(html);

    // Apply pagination if > 15 results
    if (results.length > 15) {
        $resultsContainer.append('<nav class="mt-5"><ul id="search-pagination" class="pagination justify-content-center"></ul></nav>');
        const $items = $resultsContainer.find('.search-result-item');
        const $pagination = $('#search-pagination');
        const perPage = 15;
        const totalPages = Math.ceil($items.length / perPage);
        let currentPage = 1;

        function showSearchPage(page) {
            currentPage = page;
            $items.each(function (i) {
                $(this).toggle(i >= (page - 1) * perPage && i < page * perPage);
            });
            renderSearchPagination();
        }

        function renderSearchPagination() {
            $pagination.empty();
            const prevDisabled = currentPage === 1 ? ' disabled' : '';
            $pagination.append('<li class="page-item' + prevDisabled + '"><a class="page-link rounded-pill mx-1" href="#" data-page="prev">Prev</a></li>');
            for (let i = 1; i <= totalPages; i++) {
                const active = i === currentPage ? ' active' : '';
                $pagination.append('<li class="page-item' + active + '"><a class="page-link rounded-circle mx-1" href="#" data-page="' + i + '">' + i + '</a></li>');
            }
            const nextDisabled = currentPage === totalPages ? ' disabled' : '';
            $pagination.append('<li class="page-item' + nextDisabled + '"><a class="page-link rounded-pill mx-1" href="#" data-page="next">Next</a></li>');
        }

        $pagination.on('click', '.page-link', function (e) {
            e.preventDefault();
            const page = $(this).data('page');
            if (page === 'prev' && currentPage > 1) showSearchPage(currentPage - 1);
            else if (page === 'next' && currentPage < totalPages) showSearchPage(currentPage + 1);
            else if (typeof page === 'number') showSearchPage(page);
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });

        showSearchPage(1);
    }
});
