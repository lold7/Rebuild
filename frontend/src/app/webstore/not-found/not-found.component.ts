import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';

/** Styled 404 page, used for unknown URLs and for missing/hidden products and orders. */
@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterModule],
  template: `
    <section class="container my-5 py-5 text-center">
      <div class="py-5">
        <h1 class="display-1 fw-bold text-primary mb-3">404</h1>
        <h2 class="fw-bold mb-3">Page Not Found</h2>
        <p class="text-muted fs-5 mb-4">{{ message || 'The page you are looking for does not exist.' }}</p>
        <div class="d-flex justify-content-center gap-3">
          <a routerLink="/products" class="btn btn-primary rounded-pill px-4">Browse All Products</a>
          <a routerLink="/" class="btn btn-outline-dark rounded-pill px-4">Back to Home</a>
        </div>
      </div>
    </section>
  `
})
export class NotFoundComponent {
  @Input() message = '';
}
