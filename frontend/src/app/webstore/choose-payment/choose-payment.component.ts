import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OrderService, PaymentMethodCode } from '../../core/services/order.service';
import { CartService } from '../../core/services/cart.service';
import { PaymentService } from '../../core/services/payment.service';
import { PaymentMethod } from '../../core/models/models';
import { apiErrorCode, apiErrorMessage, newIdempotencyKey } from '../../core/utils/api.utils';

type Tab = 'Credit' | 'Debit' | 'Digital' | 'Bank' | 'Other' | 'All';

/** Cart problems that send the customer back to the cart with a message. */
const CART_ERRORS = ['INSUFFICIENT_STOCK', 'OUT_OF_STOCK', 'PRODUCT_UNAVAILABLE', 'INVALID_CART_FORMAT', 'EMPTY_CART'];

/**
 * Payment step of checkout (`/checkout/payment`) and the "Manage payment methods" page
 * (`/account/payment-methods`, route data `mode: 'manage'`). Only a card's brand, last four
 * digits and expiry are ever sent to the server; nothing is charged.
 */
@Component({
  selector: 'app-choose-payment',
  standalone: true,
  imports: [
    MatIconModule, CommonModule, RouterModule, FormsModule, MatExpansionModule, MatSnackBarModule],
  templateUrl: './choose-payment.component.html',
  styleUrls: ['./choose-payment.component.scss']
})
export class ChoosePaymentComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private cartService = inject(CartService);
  private paymentService = inject(PaymentService);
  private snackBar = inject(MatSnackBar);

  manageMode = false;
  addressId: number | null = null;
  total = 0;

  tabs: Tab[] = ['Credit', 'Debit', 'Digital', 'Bank', 'Other', 'All'];
  activeTab: Tab = 'Credit';
  cards: PaymentMethod[] = [];
  selectedCardId: number | null = null;

  /** New-card form */
  newCard = { cardholderName: '', cardNumber: '', expiry: '', type: 'CREDIT' as 'CREDIT' | 'DEBIT', makeDefault: false };
  isSaving = false;
  cardError = '';

  isSubmitting = false;
  /** One key per order attempt, reused if the customer retries after a network error. */
  private idempotencyKey = newIdempotencyKey();

  ngOnInit(): void {
    this.manageMode = this.route.snapshot.data['mode'] === 'manage';

    if (this.manageMode) {
      this.loadCards();
      return;
    }

    this.route.queryParamMap.subscribe(params => {
      const addrId = params.get('address_id');
      this.addressId = addrId ? +addrId : null;
    });

    this.orderService.getCheckoutData().subscribe({
      next: (res) => {
        this.total = res.total;
        if (!res.cartItems || res.cartItems.length === 0) {
          this.router.navigate(['/cart']);
          return;
        }
        this.setCards(res.savedCards);
      },
      error: () => {
        this.router.navigate(['/cart']);
      }
    });
  }

  private loadCards(): void {
    this.paymentService.getPaymentMethods().subscribe({
      next: (cards) => this.setCards(cards),
      error: () => this.setCards([])
    });
  }

  private setCards(cards: PaymentMethod[]): void {
    this.cards = cards;
    const usable = cards.filter(c => !c.expired);
    const preferred = usable.find(c => c.is_default) ?? usable[0];
    if (!this.cards.some(c => c.payment_method_id === this.selectedCardId)) {
      this.selectedCardId = preferred ? preferred.payment_method_id : null;
    }
    if (preferred && (this.activeTab === 'Credit' || this.activeTab === 'Debit')) {
      this.activeTab = preferred.type === 'DEBIT' ? 'Debit' : 'Credit';
    }
  }

  get isCardTab(): boolean {
    return this.activeTab === 'Credit' || this.activeTab === 'Debit' || this.activeTab === 'All';
  }

  get visibleCards(): PaymentMethod[] {
    if (this.activeTab === 'Credit') return this.cards.filter(c => c.type === 'CREDIT');
    if (this.activeTab === 'Debit') return this.cards.filter(c => c.type === 'DEBIT');
    if (this.activeTab === 'All') return this.cards;
    return [];
  }

  /** What "Confirm Order" will use, shown on the page. */
  get selectedMethodLabel(): string {
    switch (this.activeTab) {
      case 'Other': return 'Cash on Delivery';
      case 'Digital': return 'Digital Wallet';
      case 'Bank': return 'Bank Transfer';
      default: {
        const card = this.selectedCard;
        return card ? `${this.brandName(card.brand)} ending in ${card.last4}` : 'No card selected';
      }
    }
  }

  get selectedCard(): PaymentMethod | undefined {
    return this.cards.find(c => c.payment_method_id === this.selectedCardId);
  }

  selectTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === 'Credit' || tab === 'Debit') {
      this.newCard.type = tab === 'Debit' ? 'DEBIT' : 'CREDIT';
      const inTab = this.visibleCards.filter(c => !c.expired);
      if (!inTab.some(c => c.payment_method_id === this.selectedCardId)) {
        this.selectedCardId = inTab.find(c => c.is_default)?.payment_method_id ?? inTab[0]?.payment_method_id ?? null;
      }
    }
  }

  selectCard(card: PaymentMethod): void {
    if (card.expired) {
      this.snackBar.open('This card has expired. Please choose another card.', 'Close', { duration: 3000 });
      return;
    }
    this.selectedCardId = card.payment_method_id;
  }

  brandName(brand: string): string {
    const names: Record<string, string> = {
      visa: 'Visa', mastercard: 'Mastercard', amex: 'American Express', discover: 'Discover',
      jcb: 'JCB', unionpay: 'UnionPay', diners: 'Diners Club', other: 'Card'
    };
    return names[brand] ?? brand;
  }

  expiryText(card: PaymentMethod): string {
    return `${String(card.expiry_month).padStart(2, '0')}/${String(card.expiry_year).slice(-2)}`;
  }

  // ---------- Manage saved cards ----------

  saveCard(): void {
    this.cardError = '';
    const digits = this.newCard.cardNumber.replace(/[\s-]/g, '');
    if (!this.newCard.cardholderName.trim()) {
      this.cardError = 'Please enter the cardholder name.';
      return;
    }
    if (!/^\d{12,19}$/.test(digits)) {
      this.cardError = 'Please enter a valid card number.';
      return;
    }
    const expiry = this.newCard.expiry.trim().match(/^(\d{1,2})\s*\/\s*(\d{2}|\d{4})$/);
    if (!expiry || +expiry[1] < 1 || +expiry[1] > 12) {
      this.cardError = 'Please enter the expiry date as MM/YY.';
      return;
    }
    const month = +expiry[1];
    const year = expiry[2].length === 2 ? 2000 + +expiry[2] : +expiry[2];

    this.isSaving = true;
    this.paymentService.addCard({
      type: this.newCard.type,
      brand: this.detectBrand(digits),
      cardholderName: this.newCard.cardholderName.trim(),
      // Only the last four digits ever leave the browser.
      last4: digits.slice(-4),
      expiryMonth: month,
      expiryYear: year,
      isDefault: this.newCard.makeDefault
    }).subscribe({
      next: (card) => {
        this.isSaving = false;
        this.newCard = { cardholderName: '', cardNumber: '', expiry: '', type: this.newCard.type, makeDefault: false };
        this.snackBar.open('Card saved', 'Close', { duration: 2500 });
        this.activeTab = card.type === 'DEBIT' ? 'Debit' : 'Credit';
        this.selectedCardId = card.payment_method_id;
        this.refreshCards();
      },
      error: (err) => {
        this.isSaving = false;
        this.cardError = apiErrorMessage(err, 'Failed to save card');
      }
    });
  }

  removeCard(card: PaymentMethod, event?: Event): void {
    event?.stopPropagation();
    if (!confirm(`Remove the card ending in ${card.last4}?`)) return;
    this.paymentService.deleteCard(card.payment_method_id).subscribe({
      next: (cards) => {
        if (this.selectedCardId === card.payment_method_id) this.selectedCardId = null;
        this.setCards(cards);
        this.snackBar.open('Card removed', 'Close', { duration: 2500 });
      },
      error: (err) => {
        const message = apiErrorCode(err) === 'LAST_ACTIVE_PAYMENT_METHOD'
          ? 'You need at least one active card on file. Add another card before removing this one.'
          : apiErrorMessage(err, 'Failed to remove card');
        this.snackBar.open(message, 'Close', { duration: 4000 });
      }
    });
  }

  makeDefault(card: PaymentMethod, event?: Event): void {
    event?.stopPropagation();
    this.paymentService.setDefault(card.payment_method_id).subscribe({
      next: (cards) => {
        this.setCards(cards);
        this.snackBar.open('Default payment method updated', 'Close', { duration: 2500 });
      },
      error: (err) => this.snackBar.open(apiErrorMessage(err, 'Failed to update default card'), 'Close', { duration: 3000 })
    });
  }

  private refreshCards(): void {
    this.paymentService.getPaymentMethods().subscribe({ next: (cards) => this.setCards(cards) });
  }

  private detectBrand(digits: string): string {
    if (/^4/.test(digits)) return 'visa';
    if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(digits)) return 'mastercard';
    if (/^3[47]/.test(digits)) return 'amex';
    if (/^(6011|65|64[4-9])/.test(digits)) return 'discover';
    if (/^35/.test(digits)) return 'jcb';
    if (/^62/.test(digits)) return 'unionpay';
    if (/^3(0[0-5]|[68])/.test(digits)) return 'diners';
    return 'other';
  }

  // ---------- Place the order ----------

  confirmOrder(): void {
    if (this.isSubmitting) return;

    let paymentMethod: PaymentMethodCode;
    let savedPaymentMethodId: number | null = null;
    if (this.activeTab === 'Other') {
      paymentMethod = 'CASH_ON_DELIVERY';
    } else if (this.activeTab === 'Digital') {
      paymentMethod = 'DIGITAL_WALLET';
    } else if (this.activeTab === 'Bank') {
      paymentMethod = 'BANK_TRANSFER';
    } else {
      const card = this.selectedCard;
      if (!card || card.expired) {
        this.snackBar.open('Please select a saved card or add a new card first.', 'Close', { duration: 3000 });
        return;
      }
      paymentMethod = card.type === 'DEBIT' ? 'DEBIT_CARD' : 'CREDIT_CARD';
      savedPaymentMethodId = card.payment_method_id;
    }

    this.isSubmitting = true;
    this.orderService.createOrder({ addressId: this.addressId, paymentMethod, savedPaymentMethodId }, this.idempotencyKey).subscribe({
      next: (order) => {
        this.cartService.setCartCount(0);
        this.snackBar.open('Order placed successfully!', 'Close', { duration: 3000 });
        this.router.navigate(['/account/orders'], { queryParams: { placed: order.order_id } });
      },
      error: (err) => {
        this.isSubmitting = false;
        const code = apiErrorCode(err);
        if (code && CART_ERRORS.includes(code)) {
          // Nothing was saved; send the customer back to the cart to fix it.
          const message = code === 'INSUFFICIENT_STOCK' || code === 'OUT_OF_STOCK'
            ? 'Not enough stock for one or more items in your cart. Please update the quantities.'
            : apiErrorMessage(err);
          this.router.navigate(['/cart'], { queryParams: { error: message } });
          return;
        }
        if (code === 'IDEMPOTENCY_KEY_REUSED') {
          this.idempotencyKey = newIdempotencyKey();
        }
        this.snackBar.open(apiErrorMessage(err, 'Failed to place order. Please try again.'), 'Close', { duration: 4000 });
      }
    });
  }
}
