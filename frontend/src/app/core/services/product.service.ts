import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Product, Category, FAQ, Paged } from '../models/models';
import { mapCategory, mapFaq, mapPage, mapProduct } from '../models/mappers';

export { mapProduct, mapCategory, mapFaq } from '../models/mappers';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  /** One 15-item page of visible products (pages start at 1). */
  getProductPage(page = 1, params?: { q?: string; category?: string | number }): Observable<Paged<Product>> {
    let httpParams = new HttpParams().set('page', String(page));
    if (params?.q) httpParams = httpParams.set('q', params.q);
    if (params?.category) httpParams = httpParams.set('category', String(params.category));
    return this.http.get<any>(`${this.apiUrl}/products`, { params: httpParams }).pipe(
      map(res => mapPage(res, mapProduct))
    );
  }

  /** The eight newest visible products, or every product (all pages) of a category. */
  getProducts(params?: { category_id?: number | string; featured?: boolean }): Observable<Product[]> {
    if (params?.featured) {
      return this.http.get<any[]>(`${this.apiUrl}/products/featured`).pipe(
        map(list => (Array.isArray(list) ? list : []).map(mapProduct))
      );
    }
    let httpParams = new HttpParams();
    if (params?.category_id) {
      httpParams = httpParams.set('category', String(params.category_id));
    }
    return this.getAllPages(`${this.apiUrl}/products`, httpParams);
  }

  getProductById(id: number | string): Observable<{ product: Product; isWishlisted: boolean }> {
    return this.http.get<any>(`${this.apiUrl}/products/${id}`).pipe(
      map(res => ({ product: mapProduct(res), isWishlisted: false }))
    );
  }

  /** Searches titles, descriptions and authors; returns every matching product. */
  searchProducts(query: string): Observable<{ query: string; products: Product[] }> {
    const params = new HttpParams().set('q', query);
    return this.getAllPages(`${this.apiUrl}/products/search`, params).pipe(
      map(products => ({ query, products }))
    );
  }

  getCategories(): Observable<Category[]> {
    return this.http.get<any[]>(`${this.apiUrl}/categories`).pipe(
      map(list => (Array.isArray(list) ? list : []).map(mapCategory))
    );
  }

  /** A visible category and all of its visible products. Errors with 404 for unknown/hidden categories. */
  getCategoryBySlug(slug: string): Observable<{ category: Category; products: Product[] }> {
    return this.http.get<any>(`${this.apiUrl}/categories/${encodeURIComponent(slug)}`).pipe(
      map(mapCategory),
      switchMap(category => this.getAllPages(`${this.apiUrl}/categories/${encodeURIComponent(slug)}/products`, new HttpParams()).pipe(
        map(products => ({ category, products }))
      ))
    );
  }

  getFaqs(): Observable<FAQ[]> {
    return this.http.get<any[]>(`${this.apiUrl}/faqs`).pipe(
      map(list => (Array.isArray(list) ? list : []).map(mapFaq))
    );
  }

  /** Fetches page 1, then any remaining pages in parallel, and concatenates them. */
  private getAllPages(url: string, params: HttpParams): Observable<Product[]> {
    return this.http.get<any>(url, { params: params.set('page', '1') }).pipe(
      switchMap(first => {
        const firstItems = (first?.items ?? []).map(mapProduct);
        const totalPages = first?.totalPages ?? 1;
        if (totalPages <= 1) return of(firstItems);
        const rest = [];
        for (let p = 2; p <= totalPages; p++) {
          rest.push(this.http.get<any>(url, { params: params.set('page', String(p)) }).pipe(
            map(res => (res?.items ?? []).map(mapProduct))
          ));
        }
        return forkJoin(rest).pipe(map(pages => firstItems.concat(...pages)));
      })
    );
  }
}
