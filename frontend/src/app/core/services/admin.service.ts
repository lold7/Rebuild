import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardStats, Category, Product, Order, CustomerSummary, Paged, OrderStatus } from '../models/models';
import {
  mapAdminOrder, mapAdminProduct, mapCategory, mapCustomer, mapPage, mapRecentOrder
} from '../models/mappers';

export interface ProductForm {
  categoryId: number;
  productName: string;
  productDescription?: string;
  productPrice: number;
  productQuantity: number;
  productImages?: string[] | null;
  productAttributes: Record<string, any>;
  expectedVersion?: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/admin`;

  // ---------- Dashboard ----------

  /** Store totals plus one 10-order page of the most recent orders. */
  getDashboard(page = 1): Observable<DashboardStats & { recentPage: number; recentTotalPages: number }> {
    const params = new HttpParams().set('page', String(page));
    return this.http.get<any>(`${this.apiUrl}/dashboard`, { params }).pipe(
      map(res => ({
        totalOrders: res.totalOrders,
        totalRevenue: res.totalRevenue,
        totalCustomers: res.totalCustomers,
        totalProducts: res.totalProducts,
        orderValue: res.orderValue,
        pendingOrders: res.pendingOrders,
        completedOrders: res.completedOrders,
        cancelledOrders: res.cancelledOrders,
        recentOrders: (res.recentOrders ?? []).map(mapRecentOrder),
        recentPage: res.recentOrdersPage ?? page,
        recentTotalPages: res.recentOrdersTotalPages ?? 1
      }))
    );
  }

  // ---------- Categories ----------

  getCategories(): Observable<Category[]> {
    return this.http.get<any[]>(`${this.apiUrl}/categories`).pipe(
      map(list => (Array.isArray(list) ? list : []).map(mapCategory))
    );
  }

  addCategory(data: { category_name: string; category_image?: string }): Observable<Category> {
    return this.http.post<any>(`${this.apiUrl}/categories`, {
      categoryName: data.category_name,
      categoryImage: data.category_image?.trim() || null
    }).pipe(map(mapCategory));
  }

  updateCategory(id: number, version: number, data: { category_name: string; category_image?: string }): Observable<Category> {
    return this.http.put<any>(`${this.apiUrl}/categories/${id}`, {
      categoryName: data.category_name,
      categoryImage: data.category_image?.trim() ?? '',
      expectedVersion: version
    }).pipe(map(mapCategory));
  }

  deleteCategory(id: number, version: number): Observable<unknown> {
    const params = new HttpParams().set('expectedVersion', String(version));
    return this.http.delete(`${this.apiUrl}/categories/${id}`, { params });
  }

  toggleCategory(id: number, version: number): Observable<Category> {
    return this.http.put<any>(`${this.apiUrl}/categories/${id}/toggle`, { expectedVersion: version }).pipe(map(mapCategory));
  }

  // ---------- Products ----------

  /** Every product (including hidden ones) matching the filters, across all pages. */
  getProducts(categoryId?: number | string, q?: string): Observable<Product[]> {
    let params = new HttpParams();
    if (categoryId) params = params.set('categoryId', String(categoryId));
    if (q && q.trim()) params = params.set('q', q.trim());
    return this.getAll(`${this.apiUrl}/products`, params, mapAdminProduct);
  }

  getProduct(id: number | string): Observable<Product> {
    return this.http.get<any>(`${this.apiUrl}/products/${id}`).pipe(map(mapAdminProduct));
  }

  /** Uploads one PNG/JPEG cover and returns the server-generated filename. */
  uploadImage(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ image: string }>(`${this.apiUrl}/product-images`, form).pipe(map(res => res.image));
  }

  addProduct(data: ProductForm): Observable<Product> {
    return this.http.post<any>(`${this.apiUrl}/products`, {
      categoryId: data.categoryId,
      productName: data.productName,
      productDescription: data.productDescription || null,
      productPrice: data.productPrice,
      productQuantity: data.productQuantity,
      productImages: data.productImages ?? [],
      productAttributes: data.productAttributes
    }).pipe(map(mapAdminProduct));
  }

  updateProduct(id: number, data: ProductForm): Observable<Product> {
    return this.http.put<any>(`${this.apiUrl}/products/${id}`, {
      categoryId: data.categoryId,
      productName: data.productName,
      productDescription: data.productDescription || null,
      productPrice: data.productPrice,
      productQuantity: data.productQuantity,
      // null keeps the existing images when no new cover was uploaded
      productImages: data.productImages ?? null,
      productAttributes: data.productAttributes,
      expectedVersion: data.expectedVersion
    }).pipe(map(mapAdminProduct));
  }

  setProductVisibility(id: number, isVisible: boolean, version: number): Observable<Product> {
    return this.http.put<any>(`${this.apiUrl}/products/${id}/visibility`, { isVisible, expectedVersion: version }).pipe(
      map(mapAdminProduct)
    );
  }

  deleteProduct(id: number, version: number): Observable<unknown> {
    const params = new HttpParams().set('expectedVersion', String(version));
    return this.http.delete(`${this.apiUrl}/products/${id}`, { params });
  }

  // ---------- Customers ----------

  getCustomers(q?: string): Observable<CustomerSummary[]> {
    let params = new HttpParams();
    if (q && q.trim()) params = params.set('q', q.trim());
    return this.getAll(`${this.apiUrl}/customers`, params, mapCustomer);
  }

  // ---------- Orders ----------

  getOrders(status?: string, q?: string): Observable<Order[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (q && q.trim()) params = params.set('q', q.trim());
    return this.getAll(`${this.apiUrl}/orders`, params, mapAdminOrder);
  }

  getOrder(id: number): Observable<Order> {
    return this.http.get<any>(`${this.apiUrl}/orders/${id}`).pipe(map(mapAdminOrder));
  }

  updateOrderStatus(id: number, status: OrderStatus, version: number): Observable<Order> {
    return this.http.put<any>(`${this.apiUrl}/orders/${id}/status`, {
      status,
      expectedVersion: version,
      reason: `Status changed to ${status} from the back office`
    }).pipe(map(mapAdminOrder));
  }

  /** Reads page 1, then the remaining pages in parallel. */
  private getAll<T>(url: string, params: HttpParams, mapItem: (x: any) => T): Observable<T[]> {
    return this.http.get<any>(url, { params: params.set('page', '1') }).pipe(
      switchMap(first => {
        const firstPage: Paged<T> = mapPage(first, mapItem);
        if (firstPage.totalPages <= 1) return of(firstPage.items);
        const rest: Observable<T[]>[] = [];
        for (let p = 2; p <= firstPage.totalPages; p++) {
          rest.push(this.http.get<any>(url, { params: params.set('page', String(p)) }).pipe(
            map(res => mapPage(res, mapItem).items)
          ));
        }
        return forkJoin(rest).pipe(map(pages => firstPage.items.concat(...pages)));
      })
    );
  }
}
