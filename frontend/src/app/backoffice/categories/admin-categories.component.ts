import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject, ViewChild, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { apiErrorMessage } from '../../core/utils/api.utils';
import { Category } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-admin-categories',
  standalone: true,
  imports: [
    MatIconModule,
    CommonModule,
    FormsModule,
    AdminSidebarComponent,
    AdminTopbarComponent,
    MatSnackBarModule,
    MatDialogModule
  ],
  templateUrl: './admin-categories.component.html',
  styleUrls: ['./admin-categories.component.scss']
})
export class AdminCategoriesComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  @ViewChild('addCategoryDialog') addCategoryDialog!: TemplateRef<any>;
  @ViewChild('editCategoryDialog') editCategoryDialog!: TemplateRef<any>;

  categories: Category[] = [];
  isLoading = true;

  // Add / Edit Dialog state
  newCategory = { category_name: '', category_image: '' };
  editingCategory: Category | null = null;
  private dialogRef: MatDialogRef<any> | null = null;

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.isLoading = true;
    this.adminService.getCategories().subscribe({
      next: (data) => {
        this.categories = data || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  openAddModal(): void {
    this.newCategory = { category_name: '', category_image: '' };
    this.dialogRef = this.dialog.open(this.addCategoryDialog, {
      width: '500px',
      autoFocus: false
    });
  }

  closeAddModal(): void {
    if (this.dialogRef) {
      this.dialogRef.close();
      this.dialogRef = null;
    }
  }

  submitAddCategory(): void {
    if (!this.newCategory.category_name.trim()) return;

    this.adminService.addCategory(this.newCategory).subscribe({
      next: () => {
        this.snackBar.open('Category added successfully', 'Close', { duration: 3000 });
        this.closeAddModal();
        this.loadCategories();
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Failed to add category'), 'Close', { duration: 3000 });
      }
    });
  }

  openEditModal(cat: Category): void {
    this.editingCategory = { ...cat };
    this.dialogRef = this.dialog.open(this.editCategoryDialog, {
      width: '500px',
      autoFocus: false
    });
  }

  closeEditModal(): void {
    if (this.dialogRef) {
      this.dialogRef.close();
      this.dialogRef = null;
    }
    this.editingCategory = null;
  }

  submitEditCategory(): void {
    if (!this.editingCategory || !this.editingCategory.category_name.trim()) return;

    this.adminService.updateCategory(this.editingCategory.category_id, this.editingCategory.version ?? 0, {
      category_name: this.editingCategory.category_name,
      category_image: this.editingCategory.category_image
    }).subscribe({
      next: () => {
        this.snackBar.open('Category updated successfully', 'Close', { duration: 3000 });
        this.closeEditModal();
        this.loadCategories();
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Failed to update category'), 'Close', { duration: 3000 });
        this.loadCategories();
      }
    });
  }

  toggleStatus(cat: Category): void {
    // Hiding a category also hides it from the menu, category pages, search and its products.
    this.adminService.toggleCategory(cat.category_id, cat.version ?? 0).subscribe({
      next: (updated) => {
        cat.is_visible = updated.is_visible;
        cat.version = updated.version;
        this.snackBar.open(updated.is_visible ? 'Category is now visible' : 'Category hidden', 'Close', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'Failed to toggle category'), 'Close', { duration: 2500 });
        this.loadCategories();
      }
    });
  }

  deleteCategory(cat: Category): void {
    const count = cat.product_count ?? 0;
    if (count > 0) {
      this.snackBar.open(`Cannot delete "${cat.category_name}": it still contains ${count} product${count === 1 ? '' : 's'}.`, 'Close', { duration: 4000 });
      return;
    }
    if (confirm(`Are you sure you want to delete category "${cat.category_name}"?`)) {
      this.adminService.deleteCategory(cat.category_id, cat.version ?? 0).subscribe({
        next: () => {
          this.snackBar.open('Category deleted successfully', 'Close', { duration: 2500 });
          this.loadCategories();
        },
        error: (err) => {
          const message = err.error?.code === 'CATEGORY_NOT_EMPTY'
            ? `Cannot delete "${cat.category_name}": it still contains products.`
            : apiErrorMessage(err, 'Failed to delete category');
          this.snackBar.open(message, 'Close', { duration: 4000 });
          this.loadCategories();
        }
      });
    }
  }
}
