import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { User } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private apiUrl = `${environment.apiUrl}/auth`;

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor() {
    this.loadUserFromStorage();
  }

  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  public get isLoggedIn(): boolean {
    return !!this.currentUserSubject.value;
  }

  public get isAdmin(): boolean {
    return this.currentUserSubject.value?.role === 'admin';
  }

  private loadUserFromStorage(): void {
    const token = localStorage.getItem('token');
    const userJson = localStorage.getItem('user');
    if (token && userJson) {
      try {
        const user = JSON.parse(userJson);
        this.currentUserSubject.next(user);
        this.checkAuth().subscribe();
      } catch (_) {
        this.logout();
      }
    }
  }

  checkAuth(): Observable<User | null> {
    const token = localStorage.getItem('token');
    if (!token) return of(null);

    return this.http.get<User>(`${this.apiUrl}/me`).pipe(
      tap(user => {
        this.setCurrentUser(user);
      }),
      catchError(() => {
        this.logout();
        return of(null);
      })
    );
  }

  login(credentials: { email: string; password: string }): Observable<{ token: string; user: User }> {
    return this.http.post<{ token: string; user: User }>(`${this.apiUrl}/login`, credentials).pipe(
      tap(res => {
        localStorage.setItem('token', res.token);
        this.setCurrentUser(res.user);
      })
    );
  }

  register(data: any): Observable<{ token: string; user: User }> {
    return this.http.post<{ token: string; user: User }>(`${this.apiUrl}/register`, data).pipe(
      tap(res => {
        localStorage.setItem('token', res.token);
        this.setCurrentUser(res.user);
      })
    );
  }

  adminLogin(credentials: { email: string; password: string }): Observable<{ token: string; user: User }> {
    return this.http.post<{ token: string; user: User }>(`${this.apiUrl}/admin/login`, credentials).pipe(
      tap(res => {
        localStorage.setItem('token', res.token);
        this.setCurrentUser(res.user);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    const wasAdmin = this.isAdmin;
    this.currentUserSubject.next(null);
    if (wasAdmin) {
      this.router.navigate(['/backoffice/login']);
    } else {
      this.router.navigate(['/']);
    }
  }

  setCurrentUser(user: User): void {
    localStorage.setItem('user', JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  updateProfile(data: { username: string; email: string; phone?: string }): Observable<any> {
    return this.http.put(`${environment.apiUrl}/account/profile`, data).pipe(
      tap((res: any) => {
        if (res.user) {
          this.setCurrentUser({ ...this.currentUserValue!, ...res.user });
        }
      })
    );
  }

  changePassword(data: { currentPassword: string; newPassword: string; confirmPassword: string }): Observable<any> {
    return this.http.put(`${environment.apiUrl}/account/password`, data);
  }
}
