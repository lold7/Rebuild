import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, finalize, map, of, switchMap, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { User } from '../models/models';
import { mapUser } from '../models/mappers';

/**
 * Session-cookie authentication against the Spring backend.
 * The server owns the session; the browser only keeps the current user in memory.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private apiUrl = environment.apiUrl;

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  public get isLoggedIn(): boolean {
    return !!this.currentUserSubject.value;
  }

  public get isAdmin(): boolean {
    return this.currentUserSubject.value?.role === 'admin';
  }

  /** Restores the session (or a Remember-me login) when the app starts. */
  init(): Observable<User | null> {
    return this.http.get<any>(`${this.apiUrl}/auth/me`).pipe(
      catchError(() => this.http.get<any>(`${this.apiUrl}/admin/auth/me`)),
      map(mapUser),
      tap(user => this.currentUserSubject.next(user)),
      catchError(() => {
        this.currentUserSubject.next(null);
        return of(null);
      })
    );
  }

  /** Kept for existing callers: re-reads the signed-in account from the server. */
  checkAuth(): Observable<User | null> {
    return this.init();
  }

  login(credentials: { email: string; password: string; rememberMe?: boolean }): Observable<User> {
    return this.http.post<any>(`${this.apiUrl}/auth/login`, {
      email: credentials.email,
      password: credentials.password,
      rememberMe: !!credentials.rememberMe
    }).pipe(
      map(mapUser),
      tap(user => this.setCurrentUser(user))
    );
  }

  /** Registers a customer account, then signs the new customer in. */
  register(data: { username: string; email: string; password: string; phone?: string }): Observable<User> {
    return this.http.post<any>(`${this.apiUrl}/auth/register`, {
      username: data.username,
      email: data.email,
      password: data.password,
      phone: data.phone || null
    }).pipe(
      switchMap(() => this.login({ email: data.email, password: data.password }))
    );
  }

  adminLogin(credentials: { email: string; password: string }): Observable<User> {
    return this.http.post<any>(`${this.apiUrl}/admin/auth/login`, {
      email: credentials.email,
      password: credentials.password
    }).pipe(
      map(mapUser),
      tap(user => this.setCurrentUser(user))
    );
  }

  /** Admin-only: creates another admin account without switching the current session. */
  createAdmin(data: { username: string; email: string; phone?: string; password: string; confirmPassword: string }): Observable<User> {
    return this.http.post<any>(`${this.apiUrl}/admin/auth/register`, {
      username: data.username,
      email: data.email,
      phone: data.phone || null,
      password: data.password,
      confirmPassword: data.confirmPassword
    }).pipe(map(mapUser));
  }

  logout(): void {
    const wasAdmin = this.isAdmin;
    this.http.post(`${this.apiUrl}/auth/logout`, {}).pipe(
      catchError(() => of(null)),
      finalize(() => {
        this.clearSession();
        this.router.navigate([wasAdmin ? '/backoffice/login' : '/']);
      })
    ).subscribe();
  }

  /** Called by the HTTP interceptor when the server reports that the session has ended. */
  handleSessionExpired(): void {
    if (!this.isLoggedIn) return;
    const wasAdmin = this.isAdmin;
    this.clearSession();
    const returnUrl = this.router.url;
    this.router.navigate([wasAdmin ? '/backoffice/login' : '/auth/login'], {
      queryParams: wasAdmin ? {} : { returnUrl }
    });
  }

  setCurrentUser(user: User): void {
    this.currentUserSubject.next(user);
  }

  private clearSession(): void {
    // Remove values written by older builds of this frontend.
    try {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    } catch { /* storage unavailable */ }
    this.currentUserSubject.next(null);
  }

  /**
   * Updates the signed-in account's name, email and phone.
   * Changing the email signs the user out on the server, so the caller is told to log in again.
   */
  updateProfile(data: { username: string; email: string; phone?: string }): Observable<{ user: User; reauthenticationRequired: boolean }> {
    const url = this.isAdmin ? `${this.apiUrl}/admin/profile` : `${this.apiUrl}/account/profile`;
    return this.http.post<any>(url, {
      username: data.username,
      email: data.email,
      phone: data.phone || ''
    }).pipe(
      map(res => ({ user: mapUser(res.user), reauthenticationRequired: !!res.reauthenticationRequired })),
      tap(res => {
        if (res.reauthenticationRequired) {
          this.clearSession();
        } else {
          this.setCurrentUser(res.user);
        }
      })
    );
  }

  /** Changing the password ends every session for the account, including this one. */
  changePassword(data: { currentPassword: string; newPassword: string; confirmPassword: string }): Observable<{ message: string }> {
    const url = this.isAdmin ? `${this.apiUrl}/admin/password` : `${this.apiUrl}/account/password`;
    return this.http.post<{ message: string }>(url, data).pipe(
      tap(() => this.clearSession())
    );
  }

  forgotPassword(email: string): Observable<{ message: string; resetLink?: string }> {
    return this.http.post<{ message: string; resetLink?: string }>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  checkResetToken(token: string): Observable<boolean> {
    return this.http.get<{ valid: boolean }>(`${this.apiUrl}/auth/reset-password/${encodeURIComponent(token)}`).pipe(
      map(res => !!res?.valid),
      catchError(() => of(false))
    );
  }

  resetPassword(token: string, newPassword: string, confirmPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/auth/reset-password/${encodeURIComponent(token)}`, {
      newPassword,
      confirmPassword
    });
  }
}
