import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CartItem, Address, Order } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getCheckoutData(): Observable<{ cartItems: CartItem[]; total: number; addresses: Address[] }> {
    return this.http.get<{ cartItems: CartItem[]; total: number; addresses: Address[] }>(`${this.apiUrl}/checkout`);
  }

  createOrder(data: { address_id?: number | null; payment_method: string }): Observable<{ success: boolean; order_id: number }> {
    return this.http.post<{ success: boolean; order_id: number }>(`${this.apiUrl}/orders`, data);
  }

  getUserOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/account/orders`);
  }

  getOrderById(id: number | string): Observable<Order> {
    return this.http.get<Order>(`${this.apiUrl}/orders/${id}`);
  }
}
