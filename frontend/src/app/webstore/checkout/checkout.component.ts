import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatRadioModule } from '@angular/material/radio';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OrderService } from '../../core/services/order.service';
import { CartItem, Address } from '../../core/models/models';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatRadioModule, MatSnackBarModule],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {
  private orderService = inject(OrderService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  cartItems: CartItem[] = [];
  addresses: Address[] = [];
  total = 0;
  selectedAddressId: number | null = null;
  isLoading = true;

  ngOnInit(): void {
    this.orderService.getCheckoutData().subscribe({
      next: (res) => {
        this.cartItems = res.cartItems || [];
        this.total = res.total || 0;
        this.addresses = res.addresses || [];
        if (this.addresses.length > 0) {
          const defaultAddr = this.addresses.find(a => a.is_default);
          this.selectedAddressId = defaultAddr ? defaultAddr.address_id : this.addresses[0].address_id;
        }
        this.isLoading = false;
        if (this.cartItems.length === 0) {
          this.router.navigate(['/cart']);
        }
      },
      error: () => {
        this.isLoading = false;
        this.router.navigate(['/cart']);
      }
    });
  }

  continueToPayment(): void {
    if (!this.selectedAddressId && this.addresses.length > 0) {
      this.snackBar.open('Please select a shipping address', 'Close', { duration: 3000 });
      return;
    }
    this.router.navigate(['/checkout/payment'], {
      queryParams: { address_id: this.selectedAddressId }
    });
  }

  getImageUrl(images: string[] | string): string {
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
