import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProductService } from '../../core/services/product.service';
import { CartService } from '../../core/services/cart.service';
import { WishlistService } from '../../core/services/wishlist.service';
import { AuthService } from '../../core/services/auth.service';
import { Product } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';
import { apiErrorCode, apiErrorMessage } from '../../core/utils/api.utils';
import { NotFoundComponent } from '../not-found/not-found.component';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    MatIconModule, CommonModule, RouterModule, FormsModule, MatSnackBarModule, NotFoundComponent],
  templateUrl: './product-detail.component.html',
  styleUrls: ['./product-detail.component.scss']
})
export class ProductDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private productService = inject(ProductService);
  private cartService = inject(CartService);
  private wishlistService = inject(WishlistService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  product: Product | null = null;
  notFound = false;
  isWishlisted = false;
  quantity = 1;
  selectedFormat = '';
  availableFormats: string[] = [];

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.loadProduct(id);
      }
    });
  }

  loadProduct(id: string): void {
    this.notFound = false;
    this.productService.getProductById(id).subscribe({
      next: (res) => {
        this.product = res.product;
        this.quantity = 1;
        this.availableFormats = this.product.product_attributes?.format ?? [];
        this.selectedFormat = this.availableFormats[0] ?? '';
        if (this.authService.isLoggedIn) {
          this.wishlistService.isWishlisted(this.product.product_id).subscribe({
            next: (saved) => this.isWishlisted = saved,
            error: () => {}
          });
        }
      },
      error: () => {
        // Missing or hidden products show the 404 page.
        this.product = null;
        this.notFound = true;
      }
    });
  }

  /** Stock labels: In Stock, Only n left, or Out of Stock. */
  get stockLabel(): string {
    const qty = this.product?.product_quantity ?? 0;
    if (!this.product?.in_stock || qty <= 0) return 'Out of Stock';
    if (qty <= 5) return `Only ${qty} left in stock`;
    return 'In Stock';
  }

  get isOutOfStock(): boolean {
    return !this.product?.in_stock || (this.product?.product_quantity ?? 0) <= 0;
  }

  /** Up to 10 copies, or the available stock if lower. */
  get maxQuantity(): number {
    return Math.max(0, Math.min(10, this.product?.max_quantity ?? 10));
  }

  decreaseQty(): void {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  increaseQty(): void {
    if (this.quantity < this.maxQuantity) {
      this.quantity++;
    }
  }

  addToCart(): void {
    if (!this.authService.isLoggedIn) {
      this.router.navigate(['/auth/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (!this.product || this.isOutOfStock) return;
    this.cartService.addToCart(this.product.product_id, this.quantity, { format: this.selectedFormat }).subscribe({
      next: () => {
        this.snackBar.open('Added to cart!', 'View Cart', { duration: 3000 }).onAction().subscribe(() => {
          this.router.navigate(['/cart']);
        });
      },
      error: (err) => {
        const code = apiErrorCode(err);
        const message = code === 'INSUFFICIENT_STOCK' || code === 'OUT_OF_STOCK'
          ? 'Not enough stock'
          : apiErrorMessage(err, 'Failed to add item to cart');
        this.snackBar.open(message, 'Close', { duration: 3000 });
      }
    });
  }

  toggleWishlist(): void {
    if (!this.authService.isLoggedIn) {
      this.router.navigate(['/auth/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (!this.product) return;
    this.wishlistService.toggleWishlist(this.product.product_id).subscribe({
      next: (res) => {
        this.isWishlisted = res.added;
        this.snackBar.open(res.added ? 'Added to wishlist!' : 'Removed from wishlist', 'Close', { duration: 2500 });
      },
      error: () => {
        this.snackBar.open('Failed to update wishlist', 'Close', { duration: 2500 });
      }
    });
  }

  getImageUrl(): string {
    return productImageUrl(this.product?.product_images);
  }
}
