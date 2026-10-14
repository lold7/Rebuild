import { HttpErrorResponse } from '@angular/common/http';

/** Builds a readable message from the backend's `{ code, message, fieldErrors }` error body. */
export function apiErrorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const httpErr = err as HttpErrorResponse;
  const body = httpErr?.error;
  if (body && typeof body === 'object') {
    const fieldErrors = body.fieldErrors && typeof body.fieldErrors === 'object' ? Object.values(body.fieldErrors) : [];
    if (fieldErrors.length > 0) {
      return fieldErrors.join(' ');
    }
    if (typeof body.message === 'string' && body.message) {
      return body.message;
    }
  }
  if (httpErr?.status === 0) {
    return 'Cannot reach the server. Please make sure the backend is running.';
  }
  return fallback;
}

/** Returns the backend error code (e.g. `INSUFFICIENT_STOCK`), if any. */
export function apiErrorCode(err: unknown): string | null {
  const body = (err as HttpErrorResponse)?.error;
  return body && typeof body === 'object' && typeof body.code === 'string' ? body.code : null;
}

/** A fresh key for POST /api/orders, which requires an `Idempotency-Key` header. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'order-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
}
