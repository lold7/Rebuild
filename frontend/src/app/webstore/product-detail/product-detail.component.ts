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

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, MatSnackBarModule],
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
  isWishlisted = false;
  quantity = 1;
  selectedFormat = 'Paperback';
  availableFormats: string[] = ['Paperback', 'Hardcover', 'E-Book'];

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.loadProduct(id);
      }
    });
  }

  loadProduct(id: string): void {
    this.productService.getProductById(id).subscribe({
      next: (res) => {
        this.product = res.product;
        this.isWishlisted = res.isWishlisted;
        if (this.product?.product_attributes?.format && this.product.product_attributes.format.length > 0) {
          this.availableFormats = this.product.product_attributes.format;
          this.selectedFormat = this.availableFormats[0];
        }
      },
      error: () => {
        this.router.navigate(['/products']);
      }
    });
  }

  decreaseQty(): void {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  increaseQty(): void {
    if (this.quantity < 10) {
      this.quantity++;
    }
  }

  addToCart(): void {
    if (!this.authService.isLoggedIn) {
      this.router.navigate(['/auth/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (!this.product) return;

    this.cartService.addToCart(this.product.product_id, this.quantity, { format: this.selectedFormat }).subscribe({
      next: () => {
        this.snackBar.open('Added to cart!', 'View Cart', { duration: 3000 }).onAction().subscribe(() => {
          this.router.navigate(['/cart']);
        });
      },
      error: () => {
        this.snackBar.open('Failed to add item to cart', 'Close', { duration: 3000 });
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
    if (this.product?.product_images && this.product.product_images.length > 0) {
      return `/images/products/${this.product.product_images[0]}`;
    }
    return '/images/Logo.png';
  }
}
