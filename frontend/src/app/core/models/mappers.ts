import { environment } from '../../../environments/environment';
import {
  Address, CartItem, CartResponse, Category, CustomerSummary, FAQ, Order, OrderItem,
  Paged, PaymentMethod, Product, User, WishlistItem
} from './models';

/*
 * The Spring Boot API uses camelCase JSON, while the Angular templates were written
 * against the original snake_case shapes. These mappers keep the templates unchanged.
 */

const num = (v: any): number => (v === null || v === undefined || v === '' ? 0 : Number(v));

function asArray(v: any): string[] {
  if (Array.isArray(v)) return v;
  if (typeof v === 'string' && v) {
    try {
      const parsed = JSON.parse(v);
      return Array.isArray(parsed) ? parsed : [v];
    } catch {
      return [v];
    }
  }
  return [];
}

function formats(attributes: any): string[] {
  const f = attributes?.format;
  if (Array.isArray(f)) return f;
  if (typeof f === 'string' && f.trim()) return [f];
  return [];
}

/** Uploaded covers are served by the backend; demo covers ship with the frontend. */
const UPLOADED_IMAGE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpe?g)$/i;

export function productImageUrl(images: string[] | string | null | undefined): string {
  const list = asArray(images);
  const first = list.length > 0 ? list[0] : null;
  if (!first) return '/images/Logo.png';
  if (/^https?:\/\//i.test(first) || first.startsWith('/')) return first;
  if (UPLOADED_IMAGE.test(first)) return `${environment.apiBase}/api/product-images/${first}`;
  return `/images/products/${first}`;
}

export function mapUser(u: any): User {
  return {
    user_id: u?.userId ?? u?.user_id,
    username: u?.username ?? '',
    email: u?.email ?? '',
    phone: u?.phone ?? '',
    role: (u?.role === 'admin' ? 'admin' : 'customer')
  };
}

export function mapProduct(p: any): Product {
  if (!p) return p;
  const attributes = p.productAttributes ?? p.product_attributes ?? {};
  return {
    product_id: p.productId ?? p.product_id,
    category_id: p.categoryId ?? p.category_id,
    category_name: p.categoryName ?? p.category_name,
    category_slug: p.categorySlug ?? p.category_slug,
    product_name: p.productName ?? p.product_name ?? '',
    product_description: p.productDescription ?? p.product_description ?? '',
    product_price: num(p.productPrice ?? p.product_price),
    product_images: asArray(p.productImages ?? p.product_images),
    product_attributes: { ...attributes, format: formats(attributes) },
    product_quantity: p.productQuantity ?? p.product_quantity,
    in_stock: p.inStock ?? (num(p.productQuantity) > 0),
    max_quantity: p.maxQuantity ?? Math.min(10, num(p.productQuantity)),
    author: attributes?.author || p.author || '',
    created_at: p.createdAt ?? p.created_at
  };
}

/** Admin product list entries: `{ product, isVisible, categoryVisible, version, updatedAt }`. */
export function mapAdminProduct(a: any): Product {
  return {
    ...mapProduct(a.product),
    is_visible: !!a.isVisible,
    category_visible: !!a.categoryVisible,
    version: a.version,
    updated_at: a.updatedAt
  };
}

export function mapCategory(c: any): Category {
  if (!c) return c;
  return {
    category_id: c.categoryId ?? c.category_id,
    category_name: c.categoryName ?? c.category_name ?? '',
    category_image: c.categoryImage ?? c.category_image ?? undefined,
    slug: c.slug,
    is_visible: c.isVisible ?? c.is_visible ?? true,
    product_count: c.productCount ?? c.product_count,
    version: c.version
  };
}

export function mapFaq(f: any): FAQ {
  return {
    faq_id: f.faqId ?? f.faq_id,
    question: f.question,
    answer: f.answer,
    sort_order: f.sortOrder ?? f.sort_order ?? 0
  };
}

export function mapCartItem(i: any): CartItem {
  const product = mapProduct(i.product);
  return {
    cart_item_id: i.cartItemId,
    product_id: product.product_id,
    quantity: i.quantity,
    product_name: product.product_name,
    author: product.author,
    product_price: num(product.product_price),
    product_images: product.product_images,
    selected_attributes: i.selectedAttributes ?? {},
    line_total: num(i.lineTotal),
    available: i.available,
    sufficient_stock: i.sufficientStock,
    valid_format: i.validFormat,
    max_quantity: product.max_quantity,
    product_quantity: product.product_quantity
  };
}

export function mapCart(c: any): CartResponse {
  const items = (c?.items ?? []).map(mapCartItem);
  return {
    cartItems: items,
    total: num(c?.totalPrice),
    cartCount: num(c?.totalQuantity),
    valid: !!c?.valid
  };
}

export function mapWishlistItem(w: any): WishlistItem {
  const product = mapProduct(w.product);
  return {
    wishlist_id: w.wishlistId,
    product_id: product.product_id,
    product_name: product.product_name,
    author: product.author,
    product_price: num(product.product_price),
    product_images: product.product_images,
    product_attributes: product.product_attributes,
    category_name: product.category_name,
    available: w.available,
    in_stock: product.in_stock
  };
}

export function mapAddress(a: any): Address {
  return {
    address_id: a.addressId ?? a.address_id,
    label: a.label ?? '',
    full_name: a.fullName ?? a.full_name ?? '',
    phone: a.phone ?? '',
    address_line: a.addressLine ?? a.address_line ?? '',
    city: a.city ?? '',
    province: a.province ?? '',
    postal_code: a.postalCode ?? a.postal_code ?? '',
    is_default: !!(a.isDefault ?? a.is_default),
    created_at: a.createdAt ?? a.created_at
  };
}

/** Converts the form's snake_case address into the API's camelCase request. */
export function toAddressRequest(a: Partial<Address>): any {
  return {
    label: a.label || null,
    fullName: a.full_name,
    phone: a.phone || null,
    addressLine: a.address_line,
    city: a.city || null,
    province: a.province || null,
    postalCode: a.postal_code || null,
    isDefault: !!a.is_default
  };
}

export function mapPaymentMethod(m: any): PaymentMethod {
  return {
    payment_method_id: m.paymentMethodId,
    type: m.type,
    brand: m.brand,
    cardholder_name: m.cardholderName,
    last4: m.last4,
    expiry_month: m.expiryMonth,
    expiry_year: m.expiryYear,
    currency: m.currency,
    is_default: !!m.isDefault,
    expired: !!m.expired,
    created_at: m.createdAt
  };
}

const PAYMENT_LABELS: Record<string, string> = {
  CREDIT_CARD: 'Credit Card',
  DEBIT_CARD: 'Debit Card',
  BANK_TRANSFER: 'Bank Transfer',
  DIGITAL_WALLET: 'Digital Wallet',
  CASH_ON_DELIVERY: 'Cash on Delivery'
};

export function paymentMethodLabel(code: string | null | undefined): string {
  if (!code) return 'N/A';
  return PAYMENT_LABELS[code] ?? code.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

export function mapOrderItem(i: any): OrderItem {
  const p = i.product ?? {};
  const attributes = p.productAttributes ?? {};
  return {
    order_item_id: i.orderItemId,
    product_id: p.productId ?? null,
    quantity: i.quantity,
    unit_price: num(i.unitPrice),
    line_total: num(i.lineTotal),
    product_name: p.productName,
    author: attributes.author,
    category_name: p.categoryName,
    product_images: asArray(p.productImages),
    selected_attributes: i.selectedAttributes ?? {}
  };
}

export function mapOrder(o: any): Order {
  const card = o.savedPaymentMethod;
  return {
    order_id: o.orderId,
    total_price: num(o.totalPrice),
    total_items: o.totalItems,
    total_quantity: o.totalQuantity,
    payment_method: paymentMethodLabel(o.paymentMethod),
    payment_status: o.paymentStatus,
    status: o.status,
    created_at: o.createdAt,
    items: (o.items ?? []).map(mapOrderItem),
    address: o.address ? mapAddress(o.address) : null,
    saved_payment_method: card ? { brand: card.brand, last4: card.last4, type: card.type } : null
  };
}

/** Admin order entries: `{ order, customer, version, stockReleased, updatedAt }`. */
export function mapAdminOrder(a: any): Order {
  return {
    ...mapOrder(a.order),
    user_id: a.customer?.userId,
    username: a.customer?.username,
    email: a.customer?.email,
    phone: a.customer?.phone,
    version: a.version,
    stock_released: a.stockReleased
  };
}

/** Dashboard recent-order summaries (no items). */
export function mapRecentOrder(o: any): Order {
  return {
    order_id: o.orderId,
    user_id: o.userId,
    username: o.username,
    status: o.status,
    payment_status: o.paymentStatus,
    payment_method: paymentMethodLabel(o.paymentMethod),
    total_price: num(o.totalPrice),
    total_items: o.totalItems,
    total_quantity: o.totalQuantity,
    created_at: o.createdAt
  };
}

export function mapCustomer(c: any): CustomerSummary {
  return {
    user_id: c.userId,
    username: c.username,
    email: c.email,
    phone: c.phone,
    created_at: c.createdAt,
    order_count: num(c.orderCount),
    // "Total spending" in the original store excludes cancelled orders.
    total_spent: num(c.orderValue),
    order_value: num(c.orderValue),
    last_order_at: c.lastOrderAt
  };
}

export function mapPage<T>(res: any, mapItem: (x: any) => T): Paged<T> {
  return {
    items: (res?.items ?? []).map(mapItem),
    page: res?.page ?? 1,
    pageSize: res?.pageSize ?? 15,
    totalItems: res?.totalItems ?? 0,
    totalPages: res?.totalPages ?? 0
  };
}
