import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Observable, expand, reduce } from 'rxjs';
import { Address, CartResponse, Category, Order, Product, User } from '../models/models';

export interface ApiPage<T> { items: T[]; page: number; totalPages: number; }
export interface ApiProduct {
  productId: number; categoryId: number; categoryName?: string; categorySlug?: string;
  productName: string; productDescription?: string; productPrice: number;
  productImages: string[]; productAttributes?: Record<string, any>;
  productQuantity: number; maxQuantity?: number; inStock?: boolean; createdAt?: string;
}
export interface ApiAddress {
  addressId: number; label: string; fullName: string; phone?: string; addressLine: string;
  city?: string; province?: string; postalCode?: string; isDefault: boolean; createdAt?: string;
}
export interface ApiCart {
  items: { cartItemId: number; product: ApiProduct; quantity: number; selectedAttributes: { format?: string }; available: boolean; sufficientStock: boolean; validFormat: boolean }[];
  totalPrice: number; totalQuantity: number; valid: boolean;
}
export interface ApiOrder {
  orderId: number; status: Order['status']; paymentStatus: string; paymentMethod: string;
  totalPrice: number; createdAt: string; totalItems?: number; totalQuantity?: number;
  address?: ApiAddress; username?: string; userId?: number;
  items?: { orderItemId: number; quantity: number; unitPrice: number; product: Partial<ApiProduct>; selectedAttributes: { format?: string } }[];
}

// Existing views consume arrays. Follow every backend page so entries after the first 15 remain accessible.
export function allPages<T>(http: HttpClient, url: string, params = new HttpParams()): Observable<T[]> {
  return http.get<ApiPage<T>>(url, { params: params.set('page', 1) }).pipe(
    expand(page => page.page < page.totalPages ? http.get<ApiPage<T>>(url, { params: params.set('page', page.page + 1) }) : EMPTY),
    reduce((items, page) => items.concat(page.items), [] as T[])
  );
}
export function userFromApi(user: { userId: number; username: string; email: string; phone?: string; role: string }): User {
  return { user_id: user.userId, username: user.username, email: user.email, phone: user.phone, role: user.role.toLowerCase() as User['role'] };
}
export function productFromApi(product: ApiProduct): Product {
  return { product_id: product.productId, category_id: product.categoryId, category_name: product.categoryName,
    product_name: product.productName, product_description: product.productDescription, product_price: product.productPrice,
    product_images: product.productImages || [], product_attributes: product.productAttributes,
    author: product.productAttributes?.['author'], created_at: product.createdAt,
    product_quantity: product.productQuantity, max_quantity: product.maxQuantity, in_stock: product.inStock };
}
export function categoryFromApi(category: any): Category {
  return { category_id: category.categoryId, category_name: category.categoryName, category_image: category.categoryImage,
    is_visible: category.isVisible ?? true, product_count: category.productCount, version: category.version, slug: category.slug };
}
export function addressFromApi(address: ApiAddress): Address {
  return { address_id: address.addressId, user_id: 0, label: address.label, full_name: address.fullName,
    phone: address.phone, address_line: address.addressLine, city: address.city, province: address.province,
    postal_code: address.postalCode, is_default: address.isDefault, created_at: address.createdAt };
}
export function cartFromApi(cart: ApiCart): CartResponse {
  return { cartItems: cart.items.map(item => ({ cart_item_id: item.cartItemId, user_id: 0, product_id: item.product.productId,
    quantity: item.quantity, product_name: item.product.productName, author: item.product.productAttributes?.['author'],
    product_price: Number(item.product.productPrice), product_images: item.product.productImages,
    selected_attributes: item.selectedAttributes, available: item.available, sufficientStock: item.sufficientStock, validFormat: item.validFormat })),
    total: Number(cart.totalPrice), cartCount: cart.totalQuantity, valid: cart.valid };
}
export function orderFromApi(order: ApiOrder): Order {
  return { order_id: order.orderId, user_id: order.userId || 0, username: order.username,
    total_price: order.totalPrice, payment_method: order.paymentMethod, payment_status: order.paymentStatus,
    status: order.status, created_at: order.createdAt, address: order.address ? addressFromApi(order.address) : null,
    items: order.items?.map(item => ({ order_item_id: item.orderItemId, order_id: order.orderId,
      product_id: item.product.productId || 0, quantity: item.quantity, unit_price: item.unitPrice,
      product_name: item.product.productName, author: item.product.productAttributes?.['author'],
      product_images: item.product.productImages || [], selected_attributes: item.selectedAttributes })) };
}
export function productImageUrl(images: string[] | string): string {
  let names: string[];
  if (typeof images === 'string') { try { names = JSON.parse(images); } catch { names = [images]; } }
  else names = images;
  const name = names?.[0];
  if (!name) return '/images/Logo.png';
  return /^[0-9a-f-]{36}\.(png|jpg)$/i.test(name) ? '/api/product-images/' + name : '/images/products/' + encodeURIComponent(name);
}
