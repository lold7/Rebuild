import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/** 403 page shown when a non-admin opens a back-office page. */
@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterModule, AsyncPipe],
  template: `
    <section class="container my-5 py-5 text-center">
      <div class="py-5">
        <h1 class="display-1 fw-bold text-primary mb-3">403</h1>
        <h2 class="fw-bold mb-3">Access Denied</h2>
        <p class="text-muted fs-5 mb-4">This page is for administrators only.</p>
        <div class="d-flex justify-content-center gap-3">
          @if (!(authService.currentUser$ | async)) {
            <a routerLink="/backoffice/login" class="btn btn-primary rounded-pill px-4">Admin Login</a>
          }
          <a routerLink="/" class="btn btn-outline-dark rounded-pill px-4">Back to Home</a>
        </div>
      </div>
    </section>
  `
})
export class ForbiddenComponent {
  authService = inject(AuthService);
}
