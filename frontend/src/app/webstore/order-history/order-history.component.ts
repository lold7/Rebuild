import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { OrderService } from '../../core/services/order.service';
import { Order } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';

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
  private route = inject(ActivatedRoute);

  orders: Order[] = [];
  isLoading = true;
  /** Set right after checkout to show the "Order placed successfully!" banner. */
  placedOrderId: string | null = null;
  page = 1;
  totalPages = 0;

  ngOnInit(): void {
    this.placedOrderId = this.route.snapshot.queryParamMap.get('placed');
    this.loadOrders();
  }

  /** Newest first, 15 per page. */
  loadOrders(page = 1): void {
    this.isLoading = true;
    this.orderService.getUserOrders(page).subscribe({
      next: (res) => {
        this.orders = res.items;
        this.page = res.page;
        this.totalPages = res.totalPages;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  getImageUrl(images: string[] | string | undefined): string {
    return productImageUrl(images);
  }
}
