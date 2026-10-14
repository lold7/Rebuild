import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../core/services/admin.service';
import { DashboardStats } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, AdminSidebarComponent, AdminTopbarComponent, MatSnackBarModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);

  stats: DashboardStats = {
    totalOrders: 0,
    totalRevenue: 0,
    totalCustomers: 0,
    totalProducts: 0,
    recentOrders: []
  };
  isLoading = true;
  /** Recent orders are shown 10 per page. */
  page = 1;
  totalPages = 0;

  ngOnInit(): void {
    this.load(1);
  }

  load(page: number): void {
    this.adminService.getDashboard(page).subscribe({
      next: (res) => {
        this.stats = res;
        this.page = res.recentPage;
        this.totalPages = res.recentTotalPages;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  generateReport(): void {
    this.snackBar.open('Monthly sales report download initiated.', 'Close', { duration: 3000 });
  }
}
