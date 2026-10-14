import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { AuthService } from './auth.service';
import { ProductService } from './product.service';
import { CartService } from './cart.service';
import { WishlistService } from './wishlist.service';
import { AddressService } from './address.service';
import { OrderService } from './order.service';
import { AdminService } from './admin.service';
import { orderFromApi, productImageUrl } from './api-adapters';

const product = { productId: 1, categoryId: 1, categoryName: 'Fiction', productName: 'The Great Gatsby',
  productPrice: 18, productImages: ['fiction-01.png'], productAttributes: { author: 'F. Scott Fitzgerald', format: ['Paperback'] },
  productQuantity: 74, inStock: true, maxQuantity: 10 };
const address = { addressId: 7, fullName: 'Alice', label: 'Home', addressLine: 'Test Street', isDefault: true };
const user = { userId: 5, username: 'Alice', email: 'alice@example.test', role: 'customer' };
const cart = (quantity = 2) => ({ items: [{ cartItemId: 9, product, quantity, selectedAttributes: { format: 'Paperback' },
  available: true, sufficientStock: true, validFormat: true }], totalPrice: 18 * quantity, totalQuantity: quantity, valid: true });
const order = { orderId: 100, status: 'pending' as const, paymentStatus: 'unpaid', paymentMethod: 'BANK_TRANSFER',
  totalPrice: 36, createdAt: '2026-10-15T00:00:00Z', address,
  items: [{ orderItemId: 11, product, quantity: 2, selectedAttributes: { format: 'Paperback' }, unitPrice: 18 }] };

describe('Spring Boot frontend integration', () => {
  let requests: HttpTestingController, auth: AuthService;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), provideRouter([])] });
    requests = TestBed.inject(HttpTestingController); auth = TestBed.inject(AuthService);
  });
  afterEach(() => requests.verify());
  function csrf(token = 'fresh-token') {
    const request = requests.expectOne('/api/auth/csrf');
    expect(request.request.withCredentials).toBeTrue();
    request.flush({ headerName: 'X-CSRF-TOKEN', token });
  }
  function customer() {
    auth.setCurrentUser({ user_id: 5, username: 'Alice', email: user.email, role: 'customer' });
  }

  it('uses cookies and a fresh CSRF token for login instead of a bearer token', () => {
    auth.login({ email: user.email, password: 'test-password', rememberMe: true }).subscribe(result => {
      expect(result.user.user_id).toBe(5); expect(auth.isLoggedIn).toBeTrue();
    });
    csrf('login-token');
    const login = requests.expectOne('/api/auth/login');
    expect(login.request.headers.get('X-CSRF-TOKEN')).toBe('login-token');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    expect(login.request.withCredentials).toBeTrue();
    expect(login.request.body.rememberMe).toBeTrue();
    login.flush(user);
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('registers an account then logs in with a newly fetched token', () => {
    auth.register({ username: 'Alice', email: user.email, password: 'test-password' }).subscribe(result => expect(result.user.role).toBe('customer'));
    csrf('register-token');
    requests.expectOne('/api/auth/register').flush(user, { status: 201, statusText: 'Created' });
    csrf('new-login-token');
    const login = requests.expectOne('/api/auth/login');
    expect(login.request.headers.get('X-CSRF-TOKEN')).toBe('new-login-token');
    login.flush(user);
  });

  it('restores an admin session from the server on reload', () => {
    auth.restoreSession().subscribe(result => expect(result?.role).toBe('admin'));
    requests.expectOne('/api/auth/me').flush({}, { status: 403, statusText: 'Forbidden' });
    requests.expectOne('/api/admin/auth/me').flush({ ...user, role: 'admin' });
    expect(auth.isAdmin).toBeTrue();
  });

  it('revokes the backend session on logout and clears the local identity', () => {
    customer(); auth.logout(); csrf();
    requests.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.currentUserValue).toBeNull();
  });

  it('loads every catalog page and adapts product metadata', () => {
    TestBed.inject(ProductService).getProducts({ category_id: 1 }).subscribe(items => {
      expect(items.length).toBe(2); expect(items[0].author).toBe('F. Scott Fitzgerald');
    });
    const first = requests.expectOne(req => req.url === '/api/products' && req.params.get('page') === '1');
    expect(first.request.params.get('category')).toBe('1');
    first.flush({ items: [product], page: 1, totalPages: 2 });
    requests.expectOne(req => req.url === '/api/products' && req.params.get('page') === '2').flush({ items: [{ ...product, productId: 2 }], page: 2, totalPages: 2 });
  });

  it('uses the featured endpoint and keeps empty pages empty', () => {
    TestBed.inject(ProductService).getProducts({ featured: true }).subscribe(items => expect(items.length).toBe(1));
    requests.expectOne('/api/products/featured').flush([product]);
    TestBed.inject(ProductService).getProducts().subscribe(items => expect(items).toEqual([]));
    requests.expectOne(req => req.url === '/api/products').flush({ items: [], page: 1, totalPages: 0 });
  });

  it('maps cart quantities/totals and posts camelCase item updates', () => {
    customer(); const service = TestBed.inject(CartService);
    requests.expectOne('/api/cart').flush(cart());
    expect(service.cartCount).toBe(2);
    service.addToCart(1, 3, { format: 'Paperback' }).subscribe(result => expect(result.total).toBe(54));
    csrf();
    const add = requests.expectOne('/api/cart/add');
    expect(add.request.body).toEqual({ productId: 1, quantity: 3, selectedFormat: 'Paperback' });
    add.flush(cart(3));
    service.updateQuantity(9, 1).subscribe();
    csrf();
    const update = requests.expectOne('/api/cart/update');
    expect(update.request.body).toEqual({ cartItemId: 9, quantity: 1 }); update.flush(cart(1));
    service.removeItem(9).subscribe(); csrf();
    const remove = requests.expectOne('/api/cart/remove');
    expect(remove.request.body).toEqual({ cartItemId: 9 }); remove.flush({ items: [], totalPrice: 0, totalQuantity: 0, valid: true });
    expect(service.cartCount).toBe(0);
  });

  it('maps wishlist saved state and refreshes its count after a mutation', () => {
    customer(); const service = TestBed.inject(WishlistService);
    requests.expectOne('/api/account/wishlist').flush({ items: [], totalItems: 0 });
    service.toggleWishlist(1).subscribe(result => { expect(result.added).toBeTrue(); expect(result.wishlistCount).toBe(1); });
    csrf();
    const toggle = requests.expectOne('/api/account/wishlist/toggle');
    expect(toggle.request.body).toEqual({ productId: 1 }); toggle.flush({ success: true, wishlisted: true });
    requests.expectOne('/api/account/wishlist').flush({ items: [{ wishlistId: 1, product, available: true }], totalItems: 1 });
  });

  it('converts address fields and uses the backend add/default/delete routes', () => {
    const service = TestBed.inject(AddressService);
    service.getAddresses().subscribe(items => expect(items[0].full_name).toBe('Alice'));
    requests.expectOne('/api/account/addresses').flush({ addresses: [address] });
    service.addAddress({ full_name: 'Alice', address_line: 'Test Street', label: 'Home' }).subscribe(result => expect(result.address_id).toBe(7));
    csrf();
    const add = requests.expectOne('/api/account/addresses/add');
    expect(add.request.body.fullName).toBe('Alice'); expect(add.request.body.addressLine).toBe('Test Street');
    expect(add.request.body.full_name).toBeUndefined(); add.flush(address);
    service.setDefaultAddress(7).subscribe(); csrf(); requests.expectOne('/api/account/addresses/default/7').flush({});
    service.deleteAddress(7).subscribe(); csrf(); requests.expectOne('/api/account/addresses/delete/7').flush({});
  });

  it('maps checkout readiness, addresses and actual saved-card metadata', () => {
    TestBed.inject(OrderService).getCheckoutData().subscribe(result => {
      expect(result.total).toBe(36); expect(result.addresses[0].address_id).toBe(7);
      expect(result.ready).toBeFalse(); expect(result.savedCards).toEqual([]);
    });
    requests.expectOne('/api/checkout').flush({ cart: cart(), addressBook: { addresses: [address] }, ready: false,
      paymentMethods: ['BANK_TRANSFER'], savedPaymentMethods: { paymentMethods: [] } });
  });

  it('reuses an idempotency key after an uncertain checkout failure', () => {
    const service = TestBed.inject(OrderService), selections = { address_id: 7, payment_method: 'Bank Transfer' };
    service.createOrder(selections).subscribe({ error: () => {} }); csrf();
    const first = requests.expectOne('/api/orders');
    const key = first.request.headers.get('Idempotency-Key');
    expect(first.request.body.paymentMethod).toBe('BANK_TRANSFER'); expect(first.request.body.addressId).toBe(7);
    expect(first.request.body.total).toBeUndefined();
    first.flush({}, { status: 500, statusText: 'Server Error' });
    service.createOrder(selections).subscribe(result => expect(result.order_id).toBe(100)); csrf();
    const retry = requests.expectOne('/api/orders'); expect(retry.request.headers.get('Idempotency-Key')).toBe(key); retry.flush(order);
    service.createOrder(selections).subscribe(); csrf();
    const next = requests.expectOne('/api/orders'); expect(next.request.headers.get('Idempotency-Key')).not.toBe(key); next.flush(order);
  });

  it('maps immutable order product and address snapshots for history', () => {
    const mapped = orderFromApi(order);
    expect(mapped.items?.[0].product_name).toBe('The Great Gatsby');
    expect(mapped.items?.[0].selected_attributes?.format).toBe('Paperback');
    expect(mapped.address?.address_line).toBe('Test Street');
    TestBed.inject(OrderService).getUserOrders().subscribe(items => expect(items[0].payment_status).toBe('unpaid'));
    requests.expectOne(req => req.url === '/api/account/orders').flush({ items: [order], page: 1, totalPages: 1 });
  });

  it('sends the displayed order version and a reason for admin status changes', () => {
    const service = TestBed.inject(AdminService);
    service.getOrders().subscribe();
    requests.expectOne(req => req.url === '/api/admin/orders').flush({ items: [{ order, customer: user, version: 2 }], page: 1, totalPages: 1 });
    service.updateOrderStatus(100, 'cancelled', 'Customer requested cancellation', 2).subscribe(result => expect(result.version).toBe(3));
    csrf();
    const update = requests.expectOne('/api/admin/orders/100/status');
    expect(update.request.body).toEqual({ status: 'cancelled', reason: 'Customer requested cancellation', expectedVersion: 2 });
    update.flush({ order: { ...order, status: 'cancelled' }, customer: user, version: 3 });
  });

  it('maps FAQ arrays to the existing accordion fields', () => {
    TestBed.inject(ProductService).getFaqs().subscribe(items => expect(items[0].faq_id).toBe(4));
    requests.expectOne('/api/faqs').flush([{ faqId: 4, question: 'Question', answer: 'Answer', sortOrder: 0 }]);
  });

  it('does not send session credentials or CSRF tokens to non-API URLs', () => {
    TestBed.inject(HttpClient).post('https://example.test/upload', {}).subscribe();
    const request = requests.expectOne('https://example.test/upload');
    expect(request.request.withCredentials).toBeFalse(); expect(request.request.headers.has('X-CSRF-TOKEN')).toBeFalse();
    request.flush({});
  });

  it('resolves both original covers and uploaded backend covers', () => {
    expect(productImageUrl(['fiction-01.png'])).toBe('/images/products/fiction-01.png');
    expect(productImageUrl(['123e4567-e89b-12d3-a456-426614174000.jpg'])).toBe('/api/product-images/123e4567-e89b-12d3-a456-426614174000.jpg');
    expect(productImageUrl([])).toBe('/images/Logo.png');
  });
});
