import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaymentMethod } from '../models/models';
import { mapPaymentMethod } from '../models/mappers';

export interface NewCard {
  type: 'CREDIT' | 'DEBIT';
  brand: string;
  cardholderName: string;
  last4: string;
  expiryMonth: number;
  expiryYear: number;
  isDefault?: boolean;
}

/** Saved cards. Only display metadata (brand, last 4 digits, expiry) is ever sent or stored. */
@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/account/payment-methods`;

  private list = (res: any): PaymentMethod[] => (res?.paymentMethods ?? []).map(mapPaymentMethod);

  getPaymentMethods(): Observable<PaymentMethod[]> {
    return this.http.get<any>(this.apiUrl).pipe(map(this.list));
  }

  addCard(card: NewCard): Observable<PaymentMethod> {
    return this.http.post<any>(`${this.apiUrl}/add`, { ...card, currency: 'USD', isDefault: !!card.isDefault }).pipe(
      map(mapPaymentMethod)
    );
  }

  deleteCard(id: number): Observable<PaymentMethod[]> {
    return this.http.post<any>(`${this.apiUrl}/delete/${id}`, {}).pipe(map(this.list));
  }

  setDefault(id: number): Observable<PaymentMethod[]> {
    return this.http.post<any>(`${this.apiUrl}/default/${id}`, {}).pipe(map(this.list));
  }
}
