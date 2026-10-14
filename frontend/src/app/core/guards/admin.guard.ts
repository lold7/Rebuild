import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Back-office pages: anyone who is not an admin sees the 403 "Access Denied" page. */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn && authService.isAdmin) {
    return true;
  }
  return router.createUrlTree(['/forbidden']);
};
