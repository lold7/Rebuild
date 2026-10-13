import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Product, Category, FAQ } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getProducts(params?: { category_id?: number | string; featured?: boolean }): Observable<Product[]> {
    let httpParams = new HttpParams();
    if (params?.category_id) {
      httpParams = httpParams.set('category_id', params.category_id.toString());
    }
    if (params?.featured) {
      httpParams = httpParams.set('featured', 'true');
    }
    return this.http.get<Product[]>(`${this.apiUrl}/products`, { params: httpParams });
  }

  getProductById(id: number | string): Observable<{ product: Product; isWishlisted: boolean }> {
    return this.http.get<{ product: Product; isWishlisted: boolean }>(`${this.apiUrl}/products/${id}`);
  }

  searchProducts(query: string): Observable<{ query: string; products: Product[] }> {
    const params = new HttpParams().set('q', query);
    return this.http.get<{ query: string; products: Product[] }>(`${this.apiUrl}/products/search`, { params });
  }

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.apiUrl}/categories`);
  }

  getCategoryBySlug(slug: string): Observable<{ category: Category; products: Product[] }> {
    return this.http.get<{ category: Category; products: Product[] }>(`${this.apiUrl}/categories/${slug}/products`);
  }

  getFaqs(): Observable<FAQ[]> {
    return this.http.get<FAQ[]>(`${this.apiUrl}/faqs`);
  }
}
