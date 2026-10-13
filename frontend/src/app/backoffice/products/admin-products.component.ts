import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { Product, Category } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-admin-products',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, AdminSidebarComponent, AdminTopbarComponent, MatSnackBarModule],
  templateUrl: './admin-products.component.html',
  styleUrls: ['./admin-products.component.scss']
})
export class AdminProductsComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);

  products: Product[] = [];
  categories: Category[] = [];
  selectedCategoryId: string = '';
  isLoading = true;

  ngOnInit(): void {
    this.loadCategories();
    this.loadProducts();
  }

  loadCategories(): void {
    this.adminService.getCategories().subscribe({
      next: (data) => this.categories = data || []
    });
  }

  loadProducts(): void {
    this.isLoading = true;
    this.adminService.getProducts(this.selectedCategoryId || undefined).subscribe({
      next: (data) => {
        this.products = data || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  onCategoryChange(): void {
    this.loadProducts();
  }

  clearFilter(): void {
    this.selectedCategoryId = '';
    this.loadProducts();
  }

  deleteProduct(p: Product): void {
    if (confirm(`Are you sure you want to delete "${p.product_name}"?`)) {
      this.adminService.deleteProduct(p.product_id).subscribe({
        next: () => {
          this.snackBar.open('Product deleted successfully', 'Close', { duration: 2500 });
          this.loadProducts();
        },
        error: () => {
          this.snackBar.open('Failed to delete product', 'Close', { duration: 2500 });
        }
      });
    }
  }

  getFirstImage(images: string[] | string): string {
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
    return '';
  }

  getAttributes(attrs: any): string {
    if (!attrs) return '-';
    if (typeof attrs === 'string') {
      try {
        attrs = JSON.parse(attrs);
      } catch (_) {
        return attrs;
      }
    }
    if (attrs.format) {
      return Array.isArray(attrs.format) ? attrs.format.join(', ') : attrs.format;
    }
    return '-';
  }
}
