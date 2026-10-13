import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { WishlistItem } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class WishlistService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/wishlist`;

  private wishlistCountSubject = new BehaviorSubject<number>(0);
  public wishlistCount$ = this.wishlistCountSubject.asObservable();

  constructor() {
    this.refreshCount();
  }

  public get wishlistCount(): number {
    return this.wishlistCountSubject.value;
  }

  public setWishlistCount(count: number): void {
    this.wishlistCountSubject.next(count);
  }

  refreshCount(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.getWishlist().subscribe({
        next: (res) => this.setWishlistCount(res.wishlistCount),
        error: () => {}
      });
    }
  }

  getWishlist(): Observable<{ wishlistItems: WishlistItem[]; wishlistCount: number }> {
    return this.http.get<{ wishlistItems: WishlistItem[]; wishlistCount: number }>(this.apiUrl).pipe(
      tap(res => this.setWishlistCount(res.wishlistCount))
    );
  }

  toggleWishlist(productId: number): Observable<{ success: boolean; added: boolean; wishlistCount: number }> {
    return this.http.post<{ success: boolean; added: boolean; wishlistCount: number }>(`${this.apiUrl}/toggle`, {
      product_id: productId
    }).pipe(
      tap(res => {
        if (typeof res.wishlistCount === 'number') {
          this.setWishlistCount(res.wishlistCount);
        }
      })
    );
  }
}
