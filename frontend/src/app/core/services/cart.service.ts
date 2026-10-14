import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CartResponse } from '../models/models';
import { mapCart } from '../models/mappers';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private apiUrl = `${environment.apiUrl}/cart`;

  private cartCountSubject = new BehaviorSubject<number>(0);
  public cartCount$ = this.cartCountSubject.asObservable();

  constructor() {
    // Keep the navbar badge in step with whoever is signed in.
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.refreshCount();
      } else {
        this.setCartCount(0);
      }
    });
  }

  public get cartCount(): number {
    return this.cartCountSubject.value;
  }

  public setCartCount(count: number): void {
    this.cartCountSubject.next(count);
  }

  refreshCount(): void {
    if (!this.authService.isLoggedIn) return;
    this.getCart().subscribe({ error: () => {} });
  }

  getCart(): Observable<CartResponse> {
    return this.http.get<any>(this.apiUrl).pipe(
      map(mapCart),
      tap(res => this.setCartCount(res.cartCount))
    );
  }

  addToCart(productId: number, quantity: number = 1, attributes: any = {}): Observable<CartResponse> {
    return this.http.post<any>(`${this.apiUrl}/add`, {
      productId,
      quantity,
      selectedFormat: attributes?.format || null
    }).pipe(
      map(mapCart),
      tap(res => this.setCartCount(res.cartCount))
    );
  }

  updateQuantity(cartItemId: number, quantity: number): Observable<CartResponse> {
    return this.http.post<any>(`${this.apiUrl}/update`, { cartItemId, quantity }).pipe(
      map(mapCart),
      tap(res => this.setCartCount(res.cartCount))
    );
  }

  removeItem(cartItemId: number): Observable<CartResponse> {
    return this.http.post<any>(`${this.apiUrl}/remove`, { cartItemId }).pipe(
      map(mapCart),
      tap(res => this.setCartCount(res.cartCount))
    );
  }

  clearCart(): Observable<CartResponse> {
    return this.http.post<any>(`${this.apiUrl}/clear`, {}).pipe(
      map(mapCart),
      tap(() => this.setCartCount(0))
    );
  }
}
