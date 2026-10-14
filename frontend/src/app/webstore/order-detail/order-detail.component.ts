import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { OrderService } from '../../core/services/order.service';
import { Order } from '../../core/models/models';
import { productImageUrl } from '../../core/models/mappers';
import { NotFoundComponent } from '../not-found/not-found.component';

/** One of the customer's orders with all of its items. Other users' orders show the 404 page. */
@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, NotFoundComponent],
  templateUrl: './order-detail.component.html'
})
export class OrderDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private orderService = inject(OrderService);

  order: Order | null = null;
  notFound = false;

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id') || '';
      this.order = null;
      this.notFound = false;
      if (!/^\d+$/.test(id)) {
        this.notFound = true;
        return;
      }
      this.orderService.getOrderById(id).subscribe({
        next: (order) => this.order = order,
        error: () => this.notFound = true
      });
    });
  }

  getImageUrl(images: string[] | undefined): string {
    return productImageUrl(images);
  }
}
