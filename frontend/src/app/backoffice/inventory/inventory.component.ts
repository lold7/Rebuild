import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { ProductService } from '../../core/services/product.service';
import { Category, Product } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, AdminSidebarComponent, AdminTopbarComponent, MatSnackBarModule],
  templateUrl: './inventory.component.html',
  styleUrls: ['./inventory.component.scss']
})
export class InventoryComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private productService = inject(ProductService);
  private snackBar = inject(MatSnackBar);

  productId: number | null = null;
  isEditMode = false;
  categories: Category[] = [];
  selectedFiles: File[] = [];
  existingImages: string[] = [];

  formModel = {
    product_name: '',
    author: '',
    isbn: '',
    product_description: '',
    product_price: '',
    discount: '',
    sku: '',
    stock: '',
    category_id: '',
    format: 'Hardcover',
    tags: ''
  };

  ngOnInit(): void {
    this.adminService.getCategories().subscribe({
      next: (data) => this.categories = data || []
    });

    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.productId = +id;
        this.isEditMode = true;
        this.loadProduct(this.productId);
      }
    });
  }

  loadProduct(id: number): void {
    this.productService.getProductById(id).subscribe({
      next: (res) => {
        const p = res.product;
        this.formModel.product_name = p.product_name;
        this.formModel.product_description = p.product_description || '';
        this.formModel.product_price = p.product_price?.toString() || '';
        this.formModel.category_id = p.category_id?.toString() || '';

        // parse images
        if (p.product_images) {
          if (Array.isArray(p.product_images)) {
            this.existingImages = p.product_images;
          } else if (typeof p.product_images === 'string') {
            try {
              this.existingImages = JSON.parse(p.product_images);
            } catch (_) {
              this.existingImages = [p.product_images];
            }
          }
        }

        // parse attributes
        if (p.product_attributes) {
          let attrs: any = p.product_attributes;
          if (typeof attrs === 'string') {
            try { attrs = JSON.parse(attrs); } catch (_) {}
          }
          this.formModel.author = attrs?.author || p.author || '';
          this.formModel.isbn = attrs?.isbn || '';
          this.formModel.discount = attrs?.discount || '';
          this.formModel.sku = attrs?.sku || '';
          this.formModel.stock = attrs?.stock || '';
          this.formModel.format = attrs?.format ? (Array.isArray(attrs.format) ? attrs.format[0] : attrs.format) : 'Hardcover';
          this.formModel.tags = attrs?.tags ? (Array.isArray(attrs.tags) ? attrs.tags.join(', ') : attrs.tags) : '';
        } else if (p.author) {
          this.formModel.author = p.author;
        }
      }
    });
  }

  onFileSelected(event: any): void {
    if (event.target.files && event.target.files.length > 0) {
      this.selectedFiles = Array.from(event.target.files);
    }
  }

  onSubmit(): void {
    if (!this.formModel.product_name || !this.formModel.product_price || !this.formModel.category_id) {
      this.snackBar.open('Please fill in required fields (Name, Price, Category)', 'Close', { duration: 3000 });
      return;
    }

    const formData = new FormData();
    formData.append('product_name', this.formModel.product_name);
    formData.append('author', this.formModel.author);
    formData.append('isbn', this.formModel.isbn);
    formData.append('product_description', this.formModel.product_description);
    formData.append('product_price', this.formModel.product_price);
    formData.append('discount', this.formModel.discount);
    formData.append('sku', this.formModel.sku);
    formData.append('stock', this.formModel.stock);
    formData.append('category_id', this.formModel.category_id);
    formData.append('format', this.formModel.format);
    formData.append('tags', this.formModel.tags);

    for (const file of this.selectedFiles) {
      formData.append('product_images', file);
    }

    if (this.isEditMode && this.productId) {
      this.adminService.updateProduct(this.productId, formData).subscribe({
        next: () => {
          this.snackBar.open('Product updated successfully!', 'Close', { duration: 3000 });
          this.router.navigate(['/backoffice/products']);
        },
        error: (err) => {
          this.snackBar.open(err.error?.message || 'Failed to update product', 'Close', { duration: 3000 });
        }
      });
    } else {
      this.adminService.addProduct(formData).subscribe({
        next: () => {
          this.snackBar.open('Product published successfully!', 'Close', { duration: 3000 });
          this.router.navigate(['/backoffice/products']);
        },
        error: (err) => {
          this.snackBar.open(err.error?.message || 'Failed to add product', 'Close', { duration: 3000 });
        }
      });
    }
  }
}
