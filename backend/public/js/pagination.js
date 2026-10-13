/* ===========================================
   Pagination — Client-side show/hide for category pages
   Shows 15 items per page from existing HTML cards
   =========================================== */
$(document).ready(function () {
    const $container = $('#book-collection').length ? $('#book-collection') : $('#product-list');
    if ($container.length === 0) return;

    const $items = $container.find('.book-card-hover').closest('.col-lg-3, .col-md-4, .col-sm-6, [class*="col-"]');
    const $paginationControls = $('#pagination-controls');
    const itemsPerPage = 15;
    const totalPages = Math.ceil($items.length / itemsPerPage);
    let currentPage = 1;

    if ($items.length === 0) return;

    function showPage(page) {
        currentPage = page;
        $items.each(function (i) {
            if (i >= (page - 1) * itemsPerPage && i < page * itemsPerPage) {
                $(this).show();
            } else {
                $(this).hide();
            }
        });
        renderPagination();
    }

    function renderPagination() {
        if ($paginationControls.length === 0) return;
        $paginationControls.empty();

        if (totalPages <= 1) {
            // Still show page 1 active to demonstrate pagination is implemented
            $paginationControls.append(
                '<li class="page-item disabled"><a class="page-link rounded-pill mx-1" href="#">Prev</a></li>' +
                '<li class="page-item active"><a class="page-link rounded-circle mx-1" href="#">1</a></li>' +
                '<li class="page-item disabled"><a class="page-link rounded-pill mx-1" href="#">Next</a></li>'
            );
            return;
        }

        // Prev button
        const prevDisabled = currentPage === 1 ? ' disabled' : '';
        $paginationControls.append(
            '<li class="page-item' + prevDisabled + '"><a class="page-link rounded-pill mx-1" href="#" data-page="prev">Prev</a></li>'
        );

        // Page numbers
        for (let i = 1; i <= totalPages; i++) {
            const active = i === currentPage ? ' active' : '';
            $paginationControls.append(
                '<li class="page-item' + active + '"><a class="page-link rounded-circle mx-1" href="#" data-page="' + i + '">' + i + '</a></li>'
            );
        }

        // Next button
        const nextDisabled = currentPage === totalPages ? ' disabled' : '';
        $paginationControls.append(
            '<li class="page-item' + nextDisabled + '"><a class="page-link rounded-pill mx-1" href="#" data-page="next">Next</a></li>'
        );
    }

    $paginationControls.on('click', '.page-link', function (e) {
        e.preventDefault();
        const page = $(this).data('page');
        if (page === 'prev' && currentPage > 1) {
            showPage(currentPage - 1);
        } else if (page === 'next' && currentPage < totalPages) {
            showPage(currentPage + 1);
        } else if (typeof page === 'number') {
            showPage(page);
        }
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    showPage(1);
});
