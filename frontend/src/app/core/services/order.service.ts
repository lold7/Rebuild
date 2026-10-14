import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Address, CartItem, Order, Paged, PaymentMethod } from '../models/models';
import { mapAddress, mapCart, mapOrder, mapPage, mapPaymentMethod } from '../models/mappers';

export interface CheckoutData {
  cartItems: CartItem[];
  total: number;
  valid: boolean;
  addresses: Address[];
  defaultAddressId: number | null;
  savedCards: PaymentMethod[];
  paymentMethods: string[];
  ready: boolean;
}

export type PaymentMethodCode = 'CREDIT_CARD' | 'DEBIT_CARD' | 'CASH_ON_DELIVERY' | 'BANK_TRANSFER' | 'DIGITAL_WALLET';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getCheckoutData(): Observable<CheckoutData> {
    return this.http.get<any>(`${this.apiUrl}/checkout`).pipe(
      map(res => {
        const cart = mapCart(res.cart);
        return {
          cartItems: cart.cartItems,
          total: cart.total,
          valid: !!cart.valid,
          addresses: (res.addressBook?.addresses ?? []).map(mapAddress),
          defaultAddressId: res.addressBook?.defaultAddressId ?? null,
          savedCards: (res.savedPaymentMethods?.paymentMethods ?? []).map(mapPaymentMethod),
          paymentMethods: res.paymentMethods ?? [],
          ready: !!res.ready
        };
      })
    );
  }

  /** Places the order. Retrying with the same `idempotencyKey` never creates a second order. */
  createOrder(data: {
    addressId?: number | null;
    paymentMethod: PaymentMethodCode;
    savedPaymentMethodId?: number | null;
  }, idempotencyKey: string): Observable<Order> {
    return this.http.post<any>(`${this.apiUrl}/orders`, {
      addressId: data.addressId ?? null,
      paymentMethod: data.paymentMethod,
      savedPaymentMethodId: data.savedPaymentMethodId ?? null
    }, { headers: { 'Idempotency-Key': idempotencyKey } }).pipe(map(mapOrder));
  }

  /** The customer's orders, newest first (15 per page). */
  getUserOrders(page = 1): Observable<Paged<Order>> {
    const params = new HttpParams().set('page', String(page));
    return this.http.get<any>(`${this.apiUrl}/account/orders`, { params }).pipe(
      map(res => mapPage(res, mapOrder))
    );
  }

  getOrderById(id: number | string): Observable<Order> {
    return this.http.get<any>(`${this.apiUrl}/orders/${id}`).pipe(map(mapOrder));
  }
}
