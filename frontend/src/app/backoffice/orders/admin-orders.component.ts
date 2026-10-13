import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { Order } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-admin-orders',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, AdminSidebarComponent, AdminTopbarComponent, MatSnackBarModule],
  templateUrl: './admin-orders.component.html',
  styleUrls: ['./admin-orders.component.scss']
})
export class AdminOrdersComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);

  orders: Order[] = [];
  statusFilter = '';
  searchQuery = '';
  isLoading = true;
  selectedOrder: Order | null = null;

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.isLoading = true;
    this.adminService.getOrders(this.statusFilter || undefined).subscribe({
      next: (data) => {
        this.orders = data || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  filterByStatus(status: string): void {
    this.statusFilter = status;
    this.loadOrders();
  }

  onStatusChange(order: Order, newStatus: string): void {
    this.adminService.updateOrderStatus(order.order_id, newStatus).subscribe({
      next: () => {
        order.status = newStatus as any;
        this.snackBar.open(`Order #${order.order_id} status updated to ${newStatus}`, 'Close', { duration: 2500 });
      },
      error: () => {
        this.snackBar.open('Failed to update order status', 'Close', { duration: 2500 });
      }
    });
  }

  get filteredOrders(): Order[] {
    if (!this.searchQuery.trim()) return this.orders;
    const q = this.searchQuery.toLowerCase();
    return this.orders.filter(o =>
      String(o.order_id).includes(q) ||
      (o.username && o.username.toLowerCase().includes(q))
    );
  }

  getInitials(name?: string): string {
    if (!name) return 'U';
    return name.split(' ').map(w => w.charAt(0).toUpperCase()).join('').substring(0, 2);
  }

  viewOrder(order: Order): void {
    this.selectedOrder = order;
  }

  closeOrderModal(): void {
    this.selectedOrder = null;
  }
}
