import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { OrderService } from '../../core/services/order.service';
import { Order } from '../../core/models/models';

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, MatExpansionModule],
  templateUrl: './order-history.component.html',
  styleUrls: ['./order-history.component.scss']
})
export class OrderHistoryComponent implements OnInit {
  private orderService = inject(OrderService);

  orders: Order[] = [];
  isLoading = true;

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.isLoading = true;
    this.orderService.getUserOrders().subscribe({
      next: (data) => {
        this.orders = data || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  getImageUrl(images: string[] | string | undefined): string {
    if (!images) return '/images/Logo.png';
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
    return '/images/Logo.png';
  }
}
