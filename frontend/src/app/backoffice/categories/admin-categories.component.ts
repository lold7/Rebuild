import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { Category } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-admin-categories',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, FormsModule, AdminSidebarComponent, AdminTopbarComponent, MatSnackBarModule],
  templateUrl: './admin-categories.component.html',
  styleUrls: ['./admin-categories.component.scss']
})
export class AdminCategoriesComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);

  categories: Category[] = [];
  isLoading = true;

  // Add / Edit Modal state
  showAddModal = false;
  showEditModal = false;
  newCategory = { category_name: '', category_image: '' };
  editingCategory: Category | null = null;

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
    this.showAddModal = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
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
        this.snackBar.open(err.error?.message || 'Failed to add category', 'Close', { duration: 3000 });
      }
    });
  }

  openEditModal(cat: Category): void {
    this.editingCategory = { ...cat };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
    this.editingCategory = null;
  }

  submitEditCategory(): void {
    if (!this.editingCategory || !this.editingCategory.category_name.trim()) return;

    this.adminService.updateCategory(this.editingCategory.category_id, {
      category_name: this.editingCategory.category_name,
      category_image: this.editingCategory.category_image
    }).subscribe({
      next: () => {
        this.snackBar.open('Category updated successfully', 'Close', { duration: 3000 });
        this.closeEditModal();
        this.loadCategories();
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to update category', 'Close', { duration: 3000 });
      }
    });
  }

  toggleStatus(cat: Category): void {
    this.adminService.toggleCategory(cat.category_id).subscribe({
      next: () => {
        cat.is_visible = !cat.is_visible;
        this.snackBar.open('Category visibility toggled', 'Close', { duration: 2500 });
      },
      error: () => {
        this.snackBar.open('Failed to toggle category', 'Close', { duration: 2500 });
      }
    });
  }

  deleteCategory(cat: Category): void {
    if (confirm(`Are you sure you want to delete category "${cat.category_name}"?`)) {
      this.adminService.deleteCategory(cat.category_id).subscribe({
        next: () => {
          this.snackBar.open('Category deleted successfully', 'Close', { duration: 2500 });
          this.loadCategories();
        },
        error: () => {
          this.snackBar.open('Failed to delete category', 'Close', { duration: 2500 });
        }
      });
    }
  }
}
