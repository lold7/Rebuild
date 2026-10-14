import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { Product, Category } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';
import { apiErrorMessage } from '../../core/utils/api.utils';
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

  /** All products matching the filters (including hidden ones). */
  products: Product[] = [];
  categories: Category[] = [];
  selectedCategoryId: string = '';
  searchQuery = '';
  isLoading = true;

  /** 10 products per page, as in the original back office. */
  readonly pageSize = 10;
  page = 1;

  ngOnInit(): void {
    this.loadCategories();
    this.loadProducts();
  }

  get pagedProducts(): Product[] {
    const start = (this.page - 1) * this.pageSize;
    return this.products.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.products.length / this.pageSize));
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  goToPage(page: number): void {
    this.page = Math.min(Math.max(1, page), this.totalPages);
  }

  loadCategories(): void {
    this.adminService.getCategories().subscribe({
      next: (data) => this.categories = data || []
    });
  }

  loadProducts(): void {
    this.isLoading = true;
    this.adminService.getProducts(this.selectedCategoryId || undefined, this.searchQuery).subscribe({
      next: (data) => {
        this.products = data || [];
        this.goToPage(this.page);
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  onCategoryChange(): void {
    this.page = 1;
    this.loadProducts();
  }

  onSearch(): void {
    this.page = 1;
    this.loadProducts();
  }

  clearFilter(): void {
    this.selectedCategoryId = '';
    this.searchQuery = '';
    this.page = 1;
    this.loadProducts();
  }

  isOutOfStock(p: Product): boolean {
    return (p.product_quantity ?? 0) <= 0;
  }

  /** Eye icon: hide or show a product on the storefront. */
  toggleVisibility(p: Product): void {
    const show = !p.is_visible;
    if (show && this.isOutOfStock(p)) {
      this.snackBar.open('This product has no stock. It will be shown as "Out of Stock".', 'Close', { duration: 3000 });
    }
    this.adminService.setProductVisibility(p.product_id, show, p.version ?? 0).subscribe({
      next: (updated) => {
        Object.assign(p, { is_visible: updated.is_visible, version: updated.version });
        this.snackBar.open(updated.is_visible ? 'Product is now visible' : 'Product hidden', 'Close', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Failed to change visibility'), 'Close', { duration: 3000 });
        this.loadProducts();
      }
    });
  }

  deleteProduct(p: Product): void {
    if (confirm(`Are you sure you want to delete "${p.product_name}"?`)) {
      this.adminService.deleteProduct(p.product_id, p.version ?? 0).subscribe({
        next: () => {
          this.snackBar.open('Product deleted successfully', 'Close', { duration: 2500 });
          this.loadProducts();
        },
        error: (err) => {
          // Products in past orders cannot be deleted; the message suggests hiding them instead.
          this.snackBar.open(apiErrorMessage(err, 'Failed to delete product'), 'Close', { duration: 5000 });
          this.loadProducts();
        }
      });
    }
  }

  getFirstImage(images: string[] | string): string {
    return productImageUrl(images);
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
    if (attrs.format && (!Array.isArray(attrs.format) || attrs.format.length > 0)) {
      return Array.isArray(attrs.format) ? attrs.format.join(', ') : attrs.format;
    }
    return '-';
  }
}
