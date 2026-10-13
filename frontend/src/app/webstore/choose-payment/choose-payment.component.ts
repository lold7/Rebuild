import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OrderService } from '../../core/services/order.service';
import { CartService } from '../../core/services/cart.service';

@Component({
  selector: 'app-choose-payment',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, MatExpansionModule, MatSnackBarModule],
  templateUrl: './choose-payment.component.html',
  styleUrls: ['./choose-payment.component.scss']
})
export class ChoosePaymentComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private cartService = inject(CartService);
  private snackBar = inject(MatSnackBar);

  addressId: number | null = null;
  total = 0;
  selectedMethod = 'Credit Card';
  activeTab = 'Credit';
  tabs = ['Credit', 'Debit', 'Digital', 'Bank', 'Other', 'All'];
  isSubmitting = false;

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const addrId = params.get('address_id');
      this.addressId = addrId ? +addrId : null;
    });

    this.orderService.getCheckoutData().subscribe({
      next: (res) => {
        this.total = res.total;
        if (!res.cartItems || res.cartItems.length === 0) {
          this.router.navigate(['/cart']);
        }
      },
      error: () => {
        this.router.navigate(['/cart']);
      }
    });
  }

  selectTab(tab: string): void {
    this.activeTab = tab;
    if (tab === 'Bank') {
      this.selectedMethod = 'Bank Transfer';
    } else if (tab === 'Digital') {
      this.selectedMethod = 'Digital Wallet';
    } else {
      this.selectedMethod = 'Credit Card';
    }
  }

  confirmOrder(): void {
    if (this.isSubmitting) return;
    this.isSubmitting = true;

    this.orderService.createOrder({
      address_id: this.addressId,
      payment_method: this.selectedMethod
    }).subscribe({
      next: () => {
        this.cartService.setCartCount(0);
        this.snackBar.open('Order placed successfully!', 'Close', { duration: 3000 });
        this.router.navigate(['/account/orders']);
      },
      error: () => {
        this.isSubmitting = false;
        this.snackBar.open('Failed to place order. Please try again.', 'Close', { duration: 3000 });
      }
    });
  }
}
