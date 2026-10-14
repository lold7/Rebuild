import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject, ViewChild, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { apiErrorMessage } from '../../core/utils/api.utils';
import { Order, OrderStatus } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-admin-orders',
  standalone: true,
  imports: [
    MatIconModule,
    CommonModule,
    RouterModule,
    FormsModule,
    AdminSidebarComponent,
    AdminTopbarComponent,
    MatSnackBarModule,
    MatDialogModule
  ],
  templateUrl: './admin-orders.component.html',
  styleUrls: ['./admin-orders.component.scss']
})
export class AdminOrdersComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  @ViewChild('orderDetailDialog') orderDetailDialog!: TemplateRef<any>;
  private dialogRef: MatDialogRef<any> | null = null;

  orders: Order[] = [];
  statusFilter = '';
  searchQuery = '';
  isLoading = true;
  selectedOrder: Order | null = null;

  ngOnInit(): void {
    this.loadOrders();
  }

  private searchTimer: any;

  loadOrders(): void {
    this.isLoading = true;
    this.adminService.getOrders(this.statusFilter || undefined, this.searchQuery).subscribe({
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

  /** Cancelling returns the items to stock; reversing a cancellation takes them out of stock again. */
  onStatusChange(order: Order, newStatus: OrderStatus): void {
    const previous = order.status;
    order.status = newStatus;
    this.adminService.updateOrderStatus(order.order_id, newStatus, order.version ?? 0).subscribe({
      next: (updated) => {
        Object.assign(order, { status: updated.status, version: updated.version, stock_released: updated.stock_released });
        this.snackBar.open(`Order #${order.order_id} status updated to ${newStatus}`, 'Close', { duration: 2500 });
        if (this.statusFilter && this.statusFilter !== newStatus) this.loadOrders();
      },
      error: (err) => {
        order.status = previous;
        const message = err.error?.code === 'INSUFFICIENT_STOCK'
          ? 'Not enough stock to reinstate this order.'
          : apiErrorMessage(err, 'Failed to update order status');
        this.snackBar.open(message, 'Close', { duration: 3500 });
        this.loadOrders();
      }
    });
  }

  /** Search by order number or customer name (on the server). */
  onSearchChange(): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.loadOrders(), 300);
  }

  get filteredOrders(): Order[] {
    return this.orders;
  }

  getInitials(name?: string): string {
    if (!name) return 'U';
    return name.split(' ').map(w => w.charAt(0).toUpperCase()).join('').substring(0, 2);
  }

  viewOrder(order: Order): void {
    this.selectedOrder = order;
    this.dialogRef = this.dialog.open(this.orderDetailDialog, {
      width: '560px',
      autoFocus: false
    });
  }

  closeOrderModal(): void {
    if (this.dialogRef) {
      this.dialogRef.close();
      this.dialogRef = null;
    }
    this.selectedOrder = null;
  }
}
