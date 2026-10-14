import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Address } from '../models/models';
import { mapAddress, toAddressRequest } from '../models/mappers';

@Injectable({
  providedIn: 'root'
})
export class AddressService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/account/addresses`;

  private book = (res: any): Address[] => (res?.addresses ?? []).map(mapAddress);

  /** Saved addresses, default first. */
  getAddresses(): Observable<Address[]> {
    return this.http.get<any>(this.apiUrl).pipe(map(this.book));
  }

  addAddress(data: Partial<Address>): Observable<{ success: boolean; address_id: number }> {
    return this.http.post<any>(`${this.apiUrl}/add`, toAddressRequest(data)).pipe(
      map(res => ({ success: true, address_id: res?.addressId }))
    );
  }

  deleteAddress(id: number): Observable<Address[]> {
    return this.http.post<any>(`${this.apiUrl}/delete/${id}`, {}).pipe(map(this.book));
  }

  setDefaultAddress(id: number): Observable<Address[]> {
    return this.http.post<any>(`${this.apiUrl}/default/${id}`, {}).pipe(map(this.book));
  }
}
