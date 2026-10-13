import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CartService } from '../../core/services/cart.service';
import { CartItem } from '../../core/models/models';

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
  private snackBar = inject(MatSnackBar);

  cartItems: CartItem[] = [];
  total = 0;
  isLoading = true;

  ngOnInit(): void {
    this.loadCart();
  }

  loadCart(): void {
    this.isLoading = true;
    this.cartService.getCart().subscribe({
      next: (res) => {
        this.cartItems = res.cartItems || [];
        this.total = res.total || 0;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  increaseQuantity(item: CartItem): void {
    const newQty = item.quantity + 1;
    this.cartService.updateQuantity(item.cart_item_id, newQty).subscribe({
      next: () => {
        item.quantity = newQty;
        this.calculateTotal();
      }
    });
  }

  decreaseQuantity(item: CartItem): void {
    if (item.quantity > 1) {
      const newQty = item.quantity - 1;
      this.cartService.updateQuantity(item.cart_item_id, newQty).subscribe({
        next: () => {
          item.quantity = newQty;
          this.calculateTotal();
        }
      });
    } else {
      this.removeItem(item);
    }
  }

  removeItem(item: CartItem): void {
    this.cartService.removeItem(item.cart_item_id).subscribe({
      next: () => {
        this.cartItems = this.cartItems.filter(i => i.cart_item_id !== item.cart_item_id);
        this.calculateTotal();
        this.snackBar.open('Item removed from cart', 'Close', { duration: 2500 });
      }
    });
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
    if (Array.isArray(images) && images.length > 0) {
      return `/images/products/${images[0]}`;
    }
    if (typeof images === 'string') {
      try {
        const arr = JSON.parse(images);
        return `/images/products/${arr[0]}`;
      } catch (_) {
        return `/images/products/${images}`;
      }
    }
    return '/images/Logo.png';
  }
}
