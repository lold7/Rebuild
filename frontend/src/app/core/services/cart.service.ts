import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CartResponse } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/cart`;

  private cartCountSubject = new BehaviorSubject<number>(0);
  public cartCount$ = this.cartCountSubject.asObservable();

  constructor() {
    this.refreshCount();
  }

  public get cartCount(): number {
    return this.cartCountSubject.value;
  }

  public setCartCount(count: number): void {
    this.cartCountSubject.next(count);
  }

  refreshCount(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.getCart().subscribe({
        next: (res) => this.setCartCount(res.cartCount),
        error: () => {}
      });
    }
  }

  getCart(): Observable<CartResponse> {
    return this.http.get<CartResponse>(this.apiUrl).pipe(
      tap(res => this.setCartCount(res.cartCount))
    );
  }

  addToCart(productId: number, quantity: number = 1, attributes: any = { format: 'Paperback' }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/add`, {
      product_id: productId,
      quantity,
      attributes
    }).pipe(
      tap(res => {
        if (typeof res.cartCount === 'number') {
          this.setCartCount(res.cartCount);
        }
      })
    );
  }

  updateQuantity(cartItemId: number, quantity: number): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${cartItemId}`, { quantity }).pipe(
      tap(res => {
        if (typeof res.cartCount === 'number') {
          this.setCartCount(res.cartCount);
        }
      })
    );
  }

  removeItem(cartItemId: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${cartItemId}`).pipe(
      tap(res => {
        if (typeof res.cartCount === 'number') {
          this.setCartCount(res.cartCount);
        }
      })
    );
  }

  clearCart(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/clear`, {}).pipe(
      tap(() => this.setCartCount(0))
    );
  }
}
