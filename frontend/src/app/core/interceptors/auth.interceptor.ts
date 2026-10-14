import { HttpBackend, HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];

/** Requests whose 401 simply means "not signed in", not "session expired". */
function isAuthProbe(url: string): boolean {
  return /\/api\/(auth|admin\/auth)\/(me|login|csrf)$/.test(url.split('?')[0]);
}

/**
 * The Spring backend authenticates with an HTTP-only session cookie and protects every
 * state-changing request with a CSRF token. This interceptor:
 *  - sends cookies with every API call (`withCredentials`),
 *  - fetches a fresh CSRF token before POST/PUT/DELETE and adds it as a header,
 *  - clears the local session if the server says the session has expired.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url !== environment.apiUrl && !req.url.startsWith(`${environment.apiUrl}/`)) {
    return next(req);
  }

  const auth = inject(AuthService);
  const csrfClient = new HttpClient(inject(HttpBackend));

  const send = (request: HttpRequest<unknown>) => next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isAuthProbe(req.url)) {
        auth.handleSessionExpired();
      }
      return throwError(() => err);
    })
  );

  const withCookies = req.clone({ withCredentials: true });
  if (SAFE_METHODS.includes(req.method.toUpperCase())) {
    return send(withCookies);
  }

  // Authentication rotates the CSRF token, so fetch a fresh one for each write.
  return csrfClient
    .get<{ headerName: string; token: string }>(`${environment.apiUrl}/auth/csrf`, { withCredentials: true })
    .pipe(switchMap(csrf => send(withCookies.clone({ setHeaders: { [csrf.headerName]: csrf.token } }))));
};
