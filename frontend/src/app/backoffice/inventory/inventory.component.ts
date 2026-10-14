import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService, ProductForm } from '../../core/services/admin.service';
import { productImageUrl } from '../../core/models/mappers';
import { apiErrorMessage } from '../../core/utils/api.utils';
import { Observable, forkJoin, of, switchMap } from 'rxjs';
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

  /** Formats saved on the product before editing; kept when the admin leaves the format unchanged. */
  private originalFormats: string[] = [];
  private version = 0;
  isSaving = false;
  readonly maxImages = 5;

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
    this.adminService.getProduct(id).subscribe({
      next: (p) => {
        const attrs: any = p.product_attributes || {};
        this.version = p.version ?? 0;
        this.existingImages = p.product_images || [];
        this.originalFormats = attrs.format || [];
        this.formModel = {
          product_name: p.product_name,
          author: attrs.author || '',
          isbn: attrs.isbn || '',
          product_description: p.product_description || '',
          product_price: p.product_price?.toString() || '',
          discount: attrs.discount !== undefined && attrs.discount !== null ? String(attrs.discount) : '',
          sku: attrs.sku || '',
          stock: String(p.product_quantity ?? 0),
          category_id: p.category_id?.toString() || '',
          format: this.originalFormats[0] || 'Hardcover',
          tags: Array.isArray(attrs.tags) ? attrs.tags.join(', ') : (attrs.tags || '')
        };
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Product not found'), 'Close', { duration: 3000 });
        this.router.navigate(['/backoffice/products']);
      }
    });
  }

  imageUrl(name: string): string {
    return productImageUrl([name]);
  }

  onFileSelected(event: any): void {
    const files: File[] = Array.from(event.target.files || []);
    const valid = files.filter(f => /^image\/(png|jpe?g)$/i.test(f.type));
    if (valid.length < files.length) {
      this.snackBar.open('Only PNG or JPEG images can be uploaded.', 'Close', { duration: 3000 });
    }
    if (valid.length > this.maxImages) {
      this.snackBar.open(`You can upload up to ${this.maxImages} images.`, 'Close', { duration: 3000 });
    }
    this.selectedFiles = valid.slice(0, this.maxImages);
  }

  onSubmit(): void {
    if (this.isSaving) return;
    const m = this.formModel;
    if (!m.product_name.trim() || !m.product_price || !m.category_id) {
      this.snackBar.open('Please fill in required fields (Name, Price, Category)', 'Close', { duration: 3000 });
      return;
    }
    const price = Number(m.product_price);
    const stock = m.stock === '' ? 0 : Number(m.stock);
    const discount = m.discount === '' ? null : Number(m.discount);
    if (!Number.isFinite(price) || price < 0) {
      this.snackBar.open('Price must be a number of 0 or more', 'Close', { duration: 3000 });
      return;
    }
    if (!Number.isInteger(stock) || stock < 0) {
      this.snackBar.open('Stock quantity must be a whole number of 0 or more', 'Close', { duration: 3000 });
      return;
    }
    if (discount !== null && (!Number.isFinite(discount) || discount < 0 || discount > 100)) {
      this.snackBar.open('Discount must be between 0 and 100', 'Close', { duration: 3000 });
      return;
    }

    const attributes: Record<string, any> = {};
    if (m.author.trim()) attributes['author'] = m.author.trim();
    if (m.isbn.trim()) attributes['isbn'] = m.isbn.trim();
    if (m.sku.trim()) attributes['sku'] = m.sku.trim();
    if (discount !== null) attributes['discount'] = Math.round(discount * 100) / 100;
    // Keep every format of an existing book unless the admin picked a different one.
    attributes['format'] = this.isEditMode && this.originalFormats.length > 0 && this.originalFormats[0] === m.format
      ? this.originalFormats
      : [m.format];
    const tags = m.tags.split(',').map(t => t.trim()).filter(Boolean);
    if (tags.length > 0) attributes['tags'] = tags;

    this.isSaving = true;
    const uploads = this.selectedFiles.map(file => this.adminService.uploadImage(file));
    const images$: Observable<string[] | null> = uploads.length > 0 ? forkJoin(uploads) : of(null);
    images$.pipe(
      switchMap(uploaded => {
        const form: ProductForm = {
          categoryId: Number(m.category_id),
          productName: m.product_name.trim(),
          productDescription: m.product_description,
          productPrice: Math.round(price * 100) / 100,
          productQuantity: stock,
          // No new upload: keep the existing images when editing.
          productImages: uploaded ?? (this.isEditMode ? null : []),
          productAttributes: attributes,
          expectedVersion: this.version
        };
        return this.isEditMode && this.productId
          ? this.adminService.updateProduct(this.productId, form)
          : this.adminService.addProduct(form);
      })
    ).subscribe({
      next: (saved) => {
        this.isSaving = false;
        let message = this.isEditMode ? 'Product updated successfully!' : 'Product published successfully!';
        if (!saved.is_visible && stock === 0) message += ' It is hidden until it is restocked.';
        this.snackBar.open(message, 'Close', { duration: 3500 });
        this.router.navigate(['/backoffice/products']);
      },
      error: (err) => {
        this.isSaving = false;
        this.snackBar.open(apiErrorMessage(err, this.isEditMode ? 'Failed to update product' : 'Failed to add product'), 'Close', { duration: 4000 });
      }
    });
  }
}
