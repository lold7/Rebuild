import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CartService } from '../../core/services/cart.service';
import { CartItem, CartResponse } from '../../core/models/models';
import { apiErrorCode, apiErrorMessage } from '../../core/utils/api.utils';
import { productImageUrl } from '../../core/models/mappers';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, MatExpansionModule, MatSnackBarModule],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss']
})
export class CartComponent implements OnInit {
  private cartService = inject(CartService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private snackBar = inject(MatSnackBar);

  cartItems: CartItem[] = [];
  total = 0;
  isLoading = true;
  /** Problems such as insufficient stock found at checkout are shown at the top of the cart. */
  errorMessage = '';

  ngOnInit(): void {
    this.errorMessage = this.route.snapshot.queryParamMap.get('error') || '';
    this.loadCart();
  }

  loadCart(): void {
    this.isLoading = true;
    this.cartService.getCart().subscribe({
      next: (res) => this.applyCart(res),
      error: () => {
        // Guests can open the cart, but it is empty.
        this.cartItems = [];
        this.total = 0;
        this.isLoading = false;
      }
    });
  }

  private applyCart(res: CartResponse): void {
    this.cartItems = res.cartItems || [];
    this.total = res.total || 0;
    this.isLoading = false;
  }

  /** Raises the quantity by one (the server still enforces the 10-copy and stock limits). */
  increaseQuantity(item: CartItem): void {
    if (item.quantity >= 10) return;
    this.updateQuantity(item, item.quantity + 1);
  }

  /** Quantities never go below 1; use Remove to delete a line. */
  decreaseQuantity(item: CartItem): void {
    if (item.quantity <= 1) return;
    this.updateQuantity(item, item.quantity - 1);
  }

  private updateQuantity(item: CartItem, quantity: number): void {
    this.cartService.updateQuantity(item.cart_item_id, quantity).subscribe({
      next: (res) => {
        this.errorMessage = '';
        this.applyCart(res);
      },
      error: (err) => {
        const code = apiErrorCode(err);
        this.snackBar.open(code === 'INSUFFICIENT_STOCK' || code === 'OUT_OF_STOCK' ? 'Not enough stock' : apiErrorMessage(err), 'Close', { duration: 3000 });
      }
    });
  }

  removeItem(item: CartItem): void {
    this.cartService.removeItem(item.cart_item_id).subscribe({
      next: (res) => {
        this.applyCart(res);
        this.snackBar.open('Item removed from cart', 'Close', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Failed to remove item'), 'Close', { duration: 2500 });
      }
    });
  }

  /** A short warning for lines that cannot be checked out as they are. */
  itemWarning(item: CartItem): string {
    if (item.available === false) return 'This book is no longer available. Please remove it.';
    if (item.valid_format === false) return 'This format is no longer offered. Please remove it.';
    if (item.sufficient_stock === false) {
      const left = item.product_quantity ?? 0;
      return left > 0 ? `Not enough stock: only ${left} left.` : 'Out of stock.';
    }
    return '';
  }

  calculateTotal(): void {
    this.total = this.cartItems.reduce((sum, item) => sum + (+item.product_price * item.quantity), 0);
  }

  proceedToCheckout(): void {
    if (this.cartItems.length > 0) {
      this.router.navigate(['/checkout']);
    }
  }

  getImageUrl(images: string[] | string): string {
    return productImageUrl(images);
  }
}
