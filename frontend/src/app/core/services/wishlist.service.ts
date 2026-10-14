import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { WishlistItem } from '../models/models';
import { mapWishlistItem } from '../models/mappers';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class WishlistService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private apiUrl = `${environment.apiUrl}/account/wishlist`;

  private wishlistCountSubject = new BehaviorSubject<number>(0);
  public wishlistCount$ = this.wishlistCountSubject.asObservable();

  constructor() {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.refreshCount();
      } else {
        this.setWishlistCount(0);
      }
    });
  }

  public get wishlistCount(): number {
    return this.wishlistCountSubject.value;
  }

  public setWishlistCount(count: number): void {
    this.wishlistCountSubject.next(count);
  }

  refreshCount(): void {
    if (!this.authService.isLoggedIn) return;
    this.getWishlist().subscribe({ error: () => {} });
  }

  getWishlist(): Observable<{ wishlistItems: WishlistItem[]; wishlistCount: number }> {
    return this.http.get<any>(this.apiUrl).pipe(
      map(res => ({
        wishlistItems: (res?.items ?? []).map(mapWishlistItem),
        wishlistCount: res?.totalItems ?? 0
      })),
      tap(res => this.setWishlistCount(res.wishlistCount))
    );
  }

  /** Whether the signed-in shopper has saved this product. */
  isWishlisted(productId: number): Observable<boolean> {
    return this.http.get<any>(`${this.apiUrl}/${productId}`).pipe(map(res => !!res?.wishlisted));
  }

  toggleWishlist(productId: number): Observable<{ success: boolean; added: boolean; wishlistCount: number }> {
    return this.http.post<any>(`${this.apiUrl}/toggle`, { productId }).pipe(
      map(res => {
        const added = !!res?.wishlisted;
        const count = Math.max(0, this.wishlistCount + (res?.action === 'added' ? 1 : res?.action === 'removed' ? -1 : 0));
        return { success: !!res?.success, added, wishlistCount: count };
      }),
      tap(res => this.setWishlistCount(res.wishlistCount))
    );
  }

  removeFromWishlist(productId: number): Observable<unknown> {
    return this.http.post<any>(`${this.apiUrl}/remove`, { productId }).pipe(
      tap((res: any) => {
        if (res?.action === 'removed') this.setWishlistCount(Math.max(0, this.wishlistCount - 1));
      })
    );
  }
}
