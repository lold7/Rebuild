import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { WishlistService } from '../../core/services/wishlist.service';
import { CartService } from '../../core/services/cart.service';
import { WishlistItem } from '../../core/models/models';
import { apiErrorMessage } from '../../core/utils/api.utils';
import { productImageUrl } from '../../core/models/mappers';

@Component({
  selector: 'app-wishlist',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, MatSnackBarModule],
  templateUrl: './wishlist.component.html',
  styleUrls: ['./wishlist.component.scss']
})
export class WishlistComponent implements OnInit {
  private wishlistService = inject(WishlistService);
  private cartService = inject(CartService);
  private snackBar = inject(MatSnackBar);

  wishlistItems: WishlistItem[] = [];
  isLoading = true;

  ngOnInit(): void {
    this.loadWishlist();
  }

  loadWishlist(): void {
    this.isLoading = true;
    this.wishlistService.getWishlist().subscribe({
      next: (res) => {
        this.wishlistItems = res.wishlistItems || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  addToCart(item: WishlistItem): void {
    this.cartService.addToCart(item.product_id, 1).subscribe({
      next: () => {
        this.snackBar.open(`"${item.product_name}" added to cart!`, 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Failed to add to cart'), 'Close', { duration: 3000 });
      }
    });
  }

  removeFromWishlist(item: WishlistItem): void {
    this.wishlistService.removeFromWishlist(item.product_id).subscribe({
      next: () => {
        this.wishlistItems = this.wishlistItems.filter(i => i.product_id !== item.product_id);
        this.snackBar.open('Removed from wishlist', 'Close', { duration: 2500 });
      },
      error: () => {
        this.snackBar.open('Failed to remove item', 'Close', { duration: 2500 });
      }
    });
  }

  clearWishlist(): void {
    if (this.wishlistItems.length === 0) return;
    const itemsToRemove = [...this.wishlistItems];
    itemsToRemove.forEach(item => {
      this.wishlistService.removeFromWishlist(item.product_id).subscribe();
    });
    this.wishlistItems = [];
    this.snackBar.open('Wishlist cleared', 'Close', { duration: 2500 });
  }

  getImageUrl(images: string[] | string): string {
    return productImageUrl(images);
  }
}
