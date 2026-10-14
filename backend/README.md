# Bookstore backend

Spring Boot 3.5.16, Spring Security, Spring Session JDBC, and PostgreSQL. This rebuild provides a public product catalog and search, customer authentication, admin authentication/authorization, profile management, password recovery, customer wishlists, persistent shopping carts, address books, saved payment method metadata, checkout, order creation, customer order history, admin product/inventory management, admin category management, admin order management, admin customer reporting, the admin dashboard, and public FAQ retrieval.

## Behaviour aligned with the original store

These rules match the original InwZa Bookstore feature inventory:

- **Payment and order status.** Checkout accepts `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `DIGITAL_WALLET` and `CASH_ON_DELIVERY`. Card orders are created as `completed`/`paid`; every other method starts `pending`/`unpaid`. Completing a Cash on Delivery order records it as `paid`.
- **Order statuses.** Orders can be `pending`, `processing`, `completed` or `cancelled`, and admins may move an order between any of them. Cancelling restores stock; moving a cancelled order back to another status deducts the stock again (or returns `409 INSUFFICIENT_STOCK` without changing anything). Reports count `processing` orders in the pending (open) totals.
- **Admins on the storefront.** Admins can use the cart, wishlist, address book, saved cards, checkout and order history with their own account. Customer profile endpoints stay customer-only.
- **Stock and visibility.** A product created with zero stock starts hidden. Setting stock to zero hides a product; restocking from zero shows it again; otherwise a manual hide is kept.
- **Deleting products.** A product that appears in past orders cannot be deleted (`409 PRODUCT_IN_ORDERS`); hide it instead.
- **Category images** may be a filename or an `http(s)` image URL.
- **Dashboard.** `GET /api/admin/dashboard?page=N` pages the recent orders ten at a time and returns `recentOrdersPage` and `recentOrdersTotalPages`.

## Run with Docker

From the `backend` directory:

```sh
cp .env.example .env
# Edit .env: set DB_PASSWORD and a random REMEMBER_ME_KEY (at least 32 characters).
# Generate a key with: openssl rand -hex 32
docker compose up --build
```

The API is available at `http://localhost:8080`. PostgreSQL is available at `localhost:5432`, with database `bookstore`. Flyway applies the schema automatically. Database data persists in the `bookstore_db` volume. Register customers through the API; first-admin setup is opt-in as described below.

## Run locally

Requires JDK 17–25 and Docker for PostgreSQL. The Maven wrapper downloads Maven automatically; no Maven installation is required.

```sh
cp .env.example .env
# Set your secrets before continuing.
docker compose up -d db
./mvnw spring-boot:run
```

Run the Maven wrapper from the `backend` directory. Spring Boot loads `.env` from that directory automatically; exported environment variables override its values. Keep entries in `KEY=value` format without shell quotes or `export`. Docker Compose also reads `.env` automatically. If using an existing PostgreSQL instance, configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` instead.

## API

All request and response bodies use JSON. Authentication uses HTTP-only cookies, not bearer tokens. Every POST requires a valid CSRF token and the cookie from the CSRF request.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Public. Returns `{ "headerName": "X-CSRF-TOKEN", "token": "..." }` and establishes a session. |
| POST | `/api/auth/register` | Public. Creates a customer, returns `201` with public account fields. Does not log the customer in. |
| POST | `/api/auth/login` | Public. Verifies credentials, rotates the session ID, returns `200` with public account fields. |
| GET | `/api/auth/me` | Returns the authenticated customer's public account fields, or `401`. |
| POST | `/api/auth/logout` | Shared customer/admin logout. Invalidates the current session, revokes the account's remembered-login tokens, clears cookies, returns `204`. |
| POST | `/api/admin/auth/login` | Public. Accepts admin credentials only; rotates the session ID and returns `200` with public admin fields. |
| GET | `/api/admin/auth/me` | Admin only. Returns the authenticated admin's public account fields. |
| POST | `/api/admin/auth/register` | Admin only. Creates another admin, returns `201`, and keeps the creator logged in. |
| POST | `/api/auth/forgot-password` | Public. Requests a 30-minute password-reset link for a customer or admin account. |
| GET | `/api/auth/reset-password/{token}` | Public. Checks a reset link without consuming it; returns `{ "valid": true }` or `400`. |
| POST | `/api/auth/reset-password/{token}` | Public. Changes the password, consumes the token, and revokes the account's sessions and remembered logins. |
| GET | `/api/account/profile` | Customer only. Returns the customer's current public profile fields. |
| POST | `/api/account/profile` | Customer only. Updates the customer's own username, email, and phone. |
| POST | `/api/account/password` | Customer only. Verifies the current password and changes it. |
| GET | `/api/admin/profile` | Admin only. Returns the admin's current public profile fields. |
| POST | `/api/admin/profile` | Admin only. Updates the admin's own username, email, and phone. |
| POST | `/api/admin/password` | Admin only. Verifies the current password and changes it. |
| GET | `/api/products` | Public. Lists visible products, 15 per page; accepts `page`, `q`, and `category`. |
| GET | `/api/products/search` | Public. Searches titles, descriptions, and authors with the same paging/filter parameters. |
| GET | `/api/products/featured` | Public. Returns the eight newest visible products. |
| GET | `/api/products/{id}` | Public. Retrieves one visible product and its stock and JSON attributes. |
| GET | `/api/categories` | Public. Lists visible categories. |
| GET | `/api/categories/{idOrSlug}` | Public. Retrieves a visible category by numeric ID or slug. |
| GET | `/api/categories/{idOrSlug}/products` | Public. Lists products in a visible category; accepts `page` and `q`. |
| GET | `/api/account/wishlist` | Customer only. Lists the customer's saved products, newest first. |
| GET | `/api/account/wishlist/{productId}` | Customer only. Returns `{ "productId": 1, "wishlisted": true }` for the customer's own saved state. |
| POST | `/api/account/wishlist/add` | Customer only. Saves a visible product; repeated adds leave it saved without duplicates. |
| POST | `/api/account/wishlist/remove` | Customer only. Removes a saved product; repeated removals leave it removed. |
| POST | `/api/account/wishlist/toggle` | Customer only. Saves an absent product or removes an existing saved product. |
| GET | `/api/account/addresses` | Customer only. Lists addresses with the default first. |
| GET | `/api/account/addresses/{id}` | Customer only. Retrieves one owned address. |
| POST | `/api/account/addresses/add` | Customer only. Adds an address; the first becomes the default automatically. |
| POST | `/api/account/addresses/delete/{id}` | Customer only. Deletes an owned address and promotes a remaining default when needed. |
| POST | `/api/account/addresses/default/{id}` | Customer only. Selects an owned address as the default. |
| GET | `/api/cart` | Customer only. Returns cart lines, quantities, current prices, totals, and availability flags. |
| POST | `/api/cart/add` | Customer only. Adds a product/format or increases an existing matching line. |
| POST | `/api/cart/update` | Customer only. Replaces an owned cart line's quantity. |
| POST | `/api/cart/remove` | Customer only. Removes an owned cart line. |
| POST | `/api/cart/clear` | Customer only. Clears the customer's cart. |
| GET | `/api/account/payment-methods` | Customer only. Lists saved cards, optionally filtered by `type=CREDIT` or `type=DEBIT`. |
| GET | `/api/account/payment-methods/{id}` | Customer only. Returns an owned saved card's display metadata. |
| POST | `/api/account/payment-methods/add` | Customer only. Saves masked card metadata; the first active card becomes the default. |
| POST | `/api/account/payment-methods/delete/{id}` | Customer only. Removes an owned card, preserving the last active card and promoting a remaining active default. |
| POST | `/api/account/payment-methods/default/{id}` | Customer only. Selects an owned, unexpired card as the default. |
| GET | `/api/checkout` | Customer only. Previews the current cart, addresses, saved cards, payment choices, and checkout readiness. |
| POST | `/api/orders` | Customer only. Creates a pending, unpaid order from the current cart; requires `Idempotency-Key` and CSRF headers. |
| GET | `/api/orders/{id}` | Customer only. Retrieves an owned order with its saved shipping, product, price, and payment metadata. |
| GET | `/api/account/orders` | Customer only. Lists owned orders, newest first, 15 per page; accepts `page` and optional `status`. |
| GET | `/api/admin/orders` | Admin only. Lists all customers' orders, 15 per page; accepts `page`, `status`, `q`, and `customerId`. |
| GET | `/api/admin/orders/{id}` | Admin only. Retrieves complete order snapshots, customer contact, and current order version. |
| PUT | `/api/admin/orders/{id}/status` | Admin only. Completes or cancels a pending order; requires `expectedVersion`, `reason`, and CSRF. Cancellation restores remaining products' stock. |
| GET | `/api/admin/orders/{id}/status-history` | Admin only. Lists recorded status changes, 15 per page; accepts `page`. |
| GET | `/api/admin/customers` | Admin only. Lists customer contacts and order/spending metrics, 15 per page; accepts `page`, `q`, `activity`, and `sort`. |
| GET | `/api/admin/customers/summary` | Admin only. Returns customer/order counts and monetary totals for the same optional `q`/`activity` filters. |
| GET | `/api/admin/customers/{id}` | Admin only. Retrieves one customer's contacts and lifetime order/spending metrics. |
| GET | `/api/admin/dashboard` | Admin only. Returns store totals, stock alerts, the 10 newest customer orders, and daily sales cohorts; accepts `days=1`–`366` (default `30`). |
| GET | `/api/faqs` | Public. Lists visible FAQs in display order; accepts optional question/answer search `q`. |
| GET | `/api/faqs/{id}` | Public. Retrieves one visible FAQ. |
| GET | `/api/admin/products`, `/api/admin/inventory` | Admin only. Lists all products, including hidden ones, with paging, search, category, visibility, and stock filters. |
| GET | `/api/admin/products/{id}` | Admin only. Retrieves product metadata, visibility, stock, and current version. |
| POST | `/api/admin/products` | Admin only. Creates a product with optional initial stock, images, attributes, and visibility. |
| PUT | `/api/admin/products/{id}` | Admin only. Updates product details; requires `expectedVersion`. |
| PUT | `/api/admin/products/{id}/visibility` | Admin only. Sets product visibility; requires `expectedVersion`. |
| PUT | `/api/admin/products/{id}/stock` | Admin only. Sets stock with a reason and `expectedVersion`, recording the adjustment transactionally. |
| DELETE | `/api/admin/products/{id}?expectedVersion=0` | Admin only. Deletes the matching version, clears cart/wishlist references, and preserves order snapshots. |
| GET | `/api/admin/products/{id}/stock-history` | Admin only. Lists recorded admin stock changes, 15 per page. |
| GET | `/api/admin/inventory/summary` | Admin only. Returns product/unit totals, low/out-of-stock counts, and visible/hidden counts. |
| GET | `/api/admin/categories` | Admin only. Lists categories, including hidden ones, with counts and versions; accepts `q` and `visibility`. |
| GET | `/api/admin/categories/{id}` | Admin only. Retrieves one category with its product count, visibility, version, and timestamps. |
| POST | `/api/admin/categories` | Admin only. Creates a category with a unique slug, optional image reference, and visibility. |
| PUT | `/api/admin/categories/{id}` | Admin only. Updates category details; requires `expectedVersion`. |
| PUT | `/api/admin/categories/{id}/visibility` | Admin only. Sets visibility; requires `expectedVersion`. |
| PUT | `/api/admin/categories/{id}/toggle` | Admin only. Flips visibility once for the supplied `expectedVersion`. |
| DELETE | `/api/admin/categories/{id}?expectedVersion=0` | Admin only. Deletes an empty category at the matching version; rejects categories containing products. |
| POST | `/api/admin/product-images` | Admin only. Accepts a multipart `file` containing a PNG or JPEG, up to 5 MiB. |
| GET | `/api/product-images/{name}` | Public. Serves an uploaded cover image using its server-generated name. |

Registration body:

```json
{
  "username": "Alice",
  "email": "alice@example.com",
  "password": "a-long-customer-password",
  "phone": "0812345678"
}
```

`phone` is optional. Username is required and limited to 100 characters; email is required, validated, trimmed, lowercased, and limited to 255 characters. Registration passwords must contain at least 8 characters and at most 72 UTF-8 bytes, matching BCrypt's input limit. Phone is limited to 20 characters. Unknown JSON fields (including `role`) are rejected; customer registration always assigns `customer` on the server.

Login body:

```json
{
  "email": "alice@example.com",
  "password": "a-long-customer-password",
  "rememberMe": true
}
```

`rememberMe` defaults to `false`. Normal login uses a browser-session cookie and a 30-minute idle session timeout. Remember me additionally issues a 30-day cookie backed by rotating PostgreSQL tokens, allowing authentication after the browser session ends. Logging in without Remember me revokes previously remembered login tokens for that customer. Logout revokes all remembered-login tokens for the customer, while other active browser sessions remain active.

Successful registration, login, and `/me` return:

```json
{
  "userId": 1,
  "username": "Alice",
  "email": "alice@example.com",
  "phone": "0812345678",
  "role": "customer"
}
```

Errors return `{ "code": "...", "message": "...", "fieldErrors": {} }`. Validation and malformed JSON return `400`; duplicate emails return `409`; invalid credentials and missing authentication return `401`; invalid CSRF tokens and forbidden access return `403`.

## Product retrieval and search

Catalog GET endpoints are available to guests, customers, and admins without a login or CSRF token. The public queries require both the product and its category to be visible. Hidden products and products in hidden categories return `404` when requested directly and are excluded from listings, featured products, and searches.

Examples:

```sh
curl 'http://localhost:8080/api/products?page=2'
curl 'http://localhost:8080/api/products/search?q=Murakami'
curl 'http://localhost:8080/api/products?q=Harry&category=children'
curl 'http://localhost:8080/api/products/featured'
curl 'http://localhost:8080/api/products/1'
curl 'http://localhost:8080/api/categories'
curl 'http://localhost:8080/api/categories/fiction/products'
```

Product listings, search results, and category listings all use 15-item pages. Pages start at 1 and are sorted by creation time descending, then product ID descending to give stable results when creation times match. Responses include:

```json
{
  "items": [],
  "page": 1,
  "pageSize": 15,
  "totalItems": 0,
  "totalPages": 0
}
```

`items` contains product objects with `productId`, `categoryId`, `categoryName`, `categorySlug`, `productName`, `productDescription`, `productPrice`, `productImages`, `productAttributes`, `productQuantity`, `inStock`, `maxQuantity`, and `createdAt`. Prices use decimal database values. Images are a JSON array; attributes are a JSON object, retaining author and available formats. `maxQuantity` is the lesser of 10 and current stock, matching the original product-detail selector.

Stock and visibility remain separate: a visible zero-stock product can still be retrieved and reports `inStock: false` and `maxQuantity: 0`. Public retrieval does not change stock or visibility.

Search is case-insensitive substring matching over product name, description, and `product_attributes.author`. It excludes other attributes such as format. Leading/trailing query whitespace is removed; a missing or blank `q` returns the regular product listing. SQL values are parameterized, and `%`, `_`, and `!` are treated as literal search characters. Category and keyword filters can be combined on `/api/products` or `/api/products/search`.

Categories accept numeric IDs or stored slugs. The original five demo categories retain their short slugs: `fiction`, `children`, `nonfiction`, `mystery`, and `science`. Empty visible categories return an empty product page. Unknown or hidden categories return `404` with code `CATALOG_NOT_FOUND`, as do invalid, missing, or hidden product IDs. Invalid page values and queries longer than 255 characters return `400` with code `INVALID_CATALOG_REQUEST`. A valid page beyond the end returns an empty `items` array while preserving the count metadata.

### Optional original demo catalog

`V3__product_catalog.sql` creates the catalog tables through Flyway. To import the original project's five categories and 60 books, add `CATALOG_DEMO_ENABLED=true` to your existing `.env` and restart the backend. `.env.example` enables this for local development. Without the setting, the schema starts empty and the catalog APIs return empty lists.

The startup import runs only if both catalog tables are empty. It uses a transaction and PostgreSQL advisory lock so multiple instances cannot import simultaneously. It preserves an existing or partially populated catalog and does not import customer accounts, credentials, or orders. The identity sequences advance past the imported IDs, allowing later products/categories to use generated IDs.

Demo data is adapted from [the original catalog SQL](https://github.com/lold7/front-end-low-cortisol/blob/main/sql/init.sql). Image fields preserve the original filenames; image binaries are not included in this backend. Serve the corresponding assets through the frontend or an image host.

## FAQ retrieval

GET `/api/faqs` returns an unpaged JSON array for the original FAQ accordion. An empty FAQ table or no visible matches returns `[]`. Guests, customers, and admins can retrieve FAQs without a session or CSRF token. Only GET is public; existing authorization and CSRF rules still protect other methods.

Each entry and GET `/api/faqs/{id}` contain exactly `faqId`, `question`, `answer`, and `sortOrder`. Rows are sorted by `sortOrder` ascending, then FAQ ID ascending for ties. Hidden FAQs are excluded from both listing/search and individual lookup. Hidden and missing IDs return the same `404`/`FAQ_NOT_FOUND` response. Invalid/nonpositive/out-of-range IDs return `400`/`INVALID_FAQ_REQUEST`.

Example response:

```json
[
  { "faqId": 1, "question": "Where can I see my orders?", "answer": "Sign in and open Order History.", "sortOrder": 10 }
]
```

GET `/api/faqs?q=shipping` searches question and answer text using case-insensitive literal substring matching. Surrounding query whitespace is ignored; missing/blank `q` returns the regular list. `%`, `_`, `!`, and SQL syntax are literal text, and all query values are parameterized. Queries longer than 255 characters return `400`/`INVALID_FAQ_REQUEST`. Reads do not modify visibility, display order, or content. Responses return stored text as JSON strings for normal text rendering.

Flyway V15 creates an empty `faqs` table. Store operators can populate their FAQ content through PostgreSQL, for example:

```sql
INSERT INTO faqs (question, answer, sort_order)
VALUES ('Where can I see my orders?', 'Sign in and open Order History.', 10);
```

Questions must be nonblank and at most 500 characters; answers must be nonblank and at most 10,000 characters. `sort_order` is a nonnegative integer, defaulting to zero; `is_visible` defaults to true. Public responses omit visibility and storage timestamps. The original frontend needs to map `faq_id` and `sort_order` to the camelCase `faqId` and `sortOrder` fields; question/answer and the array shape retain their contracts.

For demonstrations, set `FAQ_DEMO_ENABLED=true` in `.env` and restart the backend to import five sample FAQs from the original order-history accordion. It defaults to false, independently of `CATALOG_DEMO_ENABLED`, and is passed through Docker Compose. Sample shipping, tracking, and return statements are frontend copy; they do not configure those operational features. The import runs only when the entire FAQ table is empty, including hidden rows. A transaction and PostgreSQL advisory lock prevent partial imports and duplicate concurrent startup imports. Existing/custom FAQ content is preserved on restart. Sample text is stored separately in `db/demo/faqs.sql`, outside the schema migration.

## Wishlist management

Wishlist endpoints require a customer login and use the authenticated identity to resolve ownership. Guests receive `401` and admins receive `403` when supplying a valid CSRF token for POST requests. The request cannot specify another account's ID, email, or role; unknown JSON fields are rejected. Service-level authorization also binds every operation to the caller's email and customer role.

All three mutation endpoints accept a positive product ID:

```json
{ "productId": 1 }
```

Successful mutations return `200` with:

```json
{
  "success": true,
  "action": "added",
  "productId": 1,
  "wishlisted": true
}
```

Actions are `added`, `removed`, or `unchanged`. Adding an already saved product returns `unchanged` and `wishlisted: true`. Removing an absent product returns `unchanged` and `wishlisted: false`. Toggle alternates the saved state; use the explicit add/remove endpoints when retrying requests should preserve the intended state.

GET `/api/account/wishlist` returns `{ "items": [], "totalItems": 0 }` for an empty wishlist. Each saved item contains `wishlistId`, `product` (the same structured product fields used by catalog retrieval), `available`, and `savedAt`. Entries are ordered by save time descending, then wishlist ID descending. GET `/api/account/wishlist/{productId}` returns the caller's own saved state, including `false` for an absent/deleted product.

New additions require both the product and category to be visible; missing or unavailable products return `404` with code `CATALOG_NOT_FOUND`. Stock is independent: visible zero-stock books can be saved. Existing saved entries remain listed if a product or its category is later hidden, with `available: false`; customers can still remove them. `available` describes catalog visibility, while `product.inStock` describes inventory. Product details in saved entries reflect current database values.

Product IDs must fit a positive 64-bit integer. Missing/nonpositive IDs, fractional numbers, malformed JSON, and account/role injection return `400`. Invalid status-path IDs return `INVALID_WISHLIST_REQUEST`. All POST requests require the session cookie and CSRF token from GET `/api/auth/csrf`.

For a logged-in customer, use the `apiPost` helper below:

```javascript
await apiPost('/api/account/wishlist/add', { productId: 1 });
const response = await fetch(`${API}/api/account/wishlist`, { credentials: 'include' });
console.log(await response.json());
await apiPost('/api/account/wishlist/toggle', { productId: 1 });
await apiPost('/api/account/wishlist/remove', { productId: 1 });
```

Wishlist records persist across logout, password changes, and email changes because they reference the account's stable user ID. Deleting a user or product cascades to its wishlist entries. PostgreSQL enforces one entry per user/product pair, and each customer's writes lock their account row so concurrent toggles apply in order. No additional environment setting is needed; Flyway applies the new migration on startup.

## Address management

Customers can maintain multiple saved shipping addresses. Every operation resolves the owner from the authenticated identity; callers cannot supply another account's ID or role. HTTP and service-level checks restrict address-book access to customers. Guests receive `401`; admins receive `403`. All POST requests require the session cookie and CSRF token from GET `/api/auth/csrf`.

POST `/api/account/addresses/add` accepts:

```json
{
  "label": "Home",
  "fullName": "Alice Example",
  "phone": "0812345678",
  "addressLine": "123 Main Street",
  "city": "Bangkok",
  "province": "Bangkok",
  "postalCode": "10110",
  "isDefault": false
}
```

`fullName` and `addressLine` are required and limited to 200 and 500 characters. Optional limits are 50 for label, 20 for phone, 100 each for city/province, and 10 for postal code. Text is trimmed. Missing or blank label/city/province/postal code use the original project's defaults: `Home`, `Bangkok`, `Bangkok`, and `10110`. An absent or blank phone is stored as null. `isDefault` defaults to false. Unknown fields, including `userId`, `addressId`, and `role`, return `400`.

Creation returns `201` with the address fields, `addressId`, `isDefault`, and `createdAt`, plus a `Location` header for retrieving that owned address. The first address automatically becomes default even if `isDefault` is false. Adding an address with `isDefault: true` switches the default from the previous address in the same transaction.

GET `/api/account/addresses` returns:

```json
{
  "addresses": [],
  "totalAddresses": 0,
  "defaultAddressId": null
}
```

Each entry has `addressId`, `label`, `fullName`, `phone`, `addressLine`, `city`, `province`, `postalCode`, `isDefault`, and `createdAt`. The default appears first; remaining addresses are sorted by creation time descending, then address ID descending. GET `/api/account/addresses/{id}` returns the same fields for one address owned by the caller.

POST `/api/account/addresses/default/{id}` selects a default, and POST `/api/account/addresses/delete/{id}` removes an address. These require no body and return `200` with the updated address book. Selecting the current default leaves it unchanged. Deleting a nondefault address keeps the current default. Deleting the default promotes the newest remaining address. Deleting the last address returns an empty book with `defaultAddressId: null`.

Missing and foreign address IDs both return `404` with `ADDRESS_NOT_FOUND`. Ownership is verified before altering a default, so a failed request cannot clear the caller's current default or modify another account. Malformed/nonpositive/out-of-range IDs return `400` with `INVALID_ADDRESS_REQUEST`; field validation errors return `VALIDATION_ERROR`.

Using the `apiPost` helper below for a logged-in customer:

```javascript
const address = await apiPost('/api/account/addresses/add', {
  label: 'Home', fullName: 'Alice Example', addressLine: '123 Main Street'
});
await apiPost(`/api/account/addresses/default/${address.addressId}`);
const response = await fetch(`${API}/api/account/addresses`, { credentials: 'include' });
console.log(await response.json());
await apiPost(`/api/account/addresses/delete/${address.addressId}`);
```

Address mutations lock the customer row and run transactionally. A PostgreSQL partial unique index permits at most one default per customer; the service maintains a default whenever addresses remain. Concurrent additions, deletions, and selections therefore preserve the default rules. Saved addresses survive logout, email changes, and password changes because they reference the stable user ID. Deleting an account cascades to its addresses. No additional environment setting is required.

## Cart management

Cart endpoints require a customer login. Ownership comes from the authenticated identity, and both HTTP and service-level checks reject guests, admins, and attempts to operate on another customer's cart. All POST requests require the session cookie and CSRF token. Cart entries persist across logout, password changes, and email changes because they reference the stable user ID.

POST `/api/cart/add` accepts:

```json
{
  "productId": 1,
  "quantity": 2,
  "selectedFormat": "Paperback"
}
```

`productId` is required. `quantity` defaults to 1 when omitted or null. `selectedFormat` is optional; when absent/blank, the first configured format is selected. Requests match format names case-insensitively and store the canonical product format. Scalar and array format definitions are supported. Products without formats use an empty attributes object. The same book and format merge into one line; different formats remain separate.

Each line must contain 1–10 units, including the quantity after merging repeated additions. Stock is checked across every format of the same product in the customer's cart. Additions require a visible product and category and positive stock. Quantity updates repeat the visibility, format, and aggregate stock checks.

POST `/api/cart/update` replaces a line's quantity:

```json
{ "cartItemId": 1, "quantity": 3 }
```

POST `/api/cart/remove` removes that customer's line:

```json
{ "cartItemId": 1 }
```

Missing and foreign cart-item IDs both return `404` with `CART_ITEM_NOT_FOUND`, without exposing ownership. Previously hidden, out-of-stock, or discontinued-format items can still be removed or cleared. POST `/api/cart/clear` requires no body and is safe to repeat on an empty cart.

All successful cart mutations return `200` with the updated cart, in the same shape as GET `/api/cart`:

```json
{
  "items": [],
  "totalItems": 0,
  "totalQuantity": 0,
  "totalPrice": 0.00,
  "valid": false
}
```

Each item contains `cartItemId`, `product` (current catalog fields), `quantity`, `selectedAttributes`, `lineTotal`, `available`, `sufficientStock`, and `validFormat`. Items are sorted newest first. `totalItems` counts lines; `totalQuantity` counts units across all lines. Line totals and the total price use current PostgreSQL decimal prices and Java `BigDecimal`, including items that have since become unavailable. Clients cannot submit prices or totals; unknown fields on add/update/remove requests are rejected.

`available` reflects product/category visibility. `sufficientStock` checks the combined quantity across formats. `validFormat` checks whether the saved format remains configured. `valid` is true only for a nonempty cart where every line satisfies all three checks. Reading a cart reports stale entries without deleting them. Cart operations do not reserve or deduct stock; these flags describe current cart validity, not a checkout guarantee.

Invalid quantities, malformed IDs, fractional numeric IDs/quantities, and unsupported formats return `400`. A merged line exceeding ten units returns `QUANTITY_LIMIT`; invalid formats return `INVALID_FORMAT`. Missing/hidden products return `404` with `CATALOG_NOT_FOUND`. Stock conflicts return `409` with `OUT_OF_STOCK` or `INSUFFICIENT_STOCK`. Failed mutations leave the cart unchanged.

Using the `apiPost` helper below for a logged-in customer:

```javascript
const cart = await apiPost('/api/cart/add', {
  productId: 1, quantity: 2, selectedFormat: 'Paperback'
});
const cartItemId = cart.items[0].cartItemId;
await apiPost('/api/cart/update', { cartItemId, quantity: 3 });
await apiPost('/api/cart/remove', { cartItemId });
await apiPost('/api/cart/clear');
```

PostgreSQL prevents duplicate customer/book/format lines, including products without a format. Account-row locks serialize each customer's writes, and shared product/category locks keep stock and visibility stable during a mutation. Deleting a user or product cascades to its cart entries. No additional environment setting is required; Flyway applies the new schema automatically.

## Saved payment methods

The original payment page contains placeholder saved cards and controls for saving, removing, and choosing a default. These APIs persist the corresponding credit/debit card display metadata for authenticated customers. Account ownership comes from the session; all mutations require CSRF. Guests and admins cannot access customer payment methods.

POST `/api/account/payment-methods/add`:

```json
{
  "type": "CREDIT",
  "brand": "visa",
  "cardholderName": "Alice Example",
  "last4": "4242",
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "isDefault": false
}
```

Returns `201` and a `Location` header pointing to `/api/account/payment-methods/{paymentMethodId}`. The response includes `paymentMethodId`, the submitted normalized display fields, `isDefault`, `expired`, and `createdAt`. `last4` must be exactly four digits and is a string to preserve leading zeros. Cardholder names are required and limited to 200 characters. Types are `CREDIT` or `DEBIT`; brands are `visa`, `mastercard`, `amex`, `discover`, `jcb`, `unionpay`, `diners`, or `other`. Type/brand matching ignores case and surrounding whitespace. Currency defaults to `USD`, accepts ISO currency codes, and is normalized to uppercase. Expiry month/year must be integers in 1–12 and 2000–9999, respectively, and the card must be unexpired when saved.

The table and API store display metadata only. Full card numbers, CVV/CVC, and processor tokens are excluded from the schema and request contract; submitting those extra fields returns `400`. This feature manages account preferences, and checkout can snapshot a selected saved card. Tokenization, verification of card ownership, payment charging, wallets, and bank accounts require a separate payment-provider integration. The existing frontend remains unchanged.

GET `/api/account/payment-methods` returns:

```json
{
  "paymentMethods": [
    {
      "paymentMethodId": 1,
      "type": "CREDIT",
      "brand": "visa",
      "cardholderName": "Alice Example",
      "last4": "4242",
      "expiryMonth": 12,
      "expiryYear": 2030,
      "currency": "USD",
      "isDefault": true,
      "expired": false,
      "createdAt": "2026-10-15T00:00:00Z"
    }
  ],
  "totalMethods": 1,
  "defaultPaymentMethodId": 1
}
```

The default appears first, followed by newest cards. Optional `type=CREDIT` or `type=DEBIT` filtering is case insensitive; omit `type` to list all cards. `totalMethods` counts the filtered results, while `defaultPaymentMethodId` identifies the account's default even when it is outside the filter. A new account has an empty list and a null default. Cards remain active through the end of their expiry month in UTC. Reads report expired cards without deleting them; an existing default can expire and must then be replaced. Adding an active card when the current default is expired automatically makes the new card the default.

POST `/api/account/payment-methods/default/{id}` returns the updated list and selects an owned, unexpired card. Selecting the current default is idempotent. POST `/api/account/payment-methods/delete/{id}` returns the updated list, retaining an unaffected active default or promoting the newest remaining active card. Following the original payment page's last-active-card rule, deleting the last unexpired card returns `409` with `LAST_ACTIVE_PAYMENT_METHOD`; add a replacement first. Expired cards can always be removed, including the final expired card.

Missing and foreign IDs both return `404` with `PAYMENT_METHOD_NOT_FOUND`. Malformed or nonpositive IDs, unsupported types/brands/currencies, and invalid filters return `400` with `INVALID_PAYMENT_METHOD_REQUEST`. Field validation returns `VALIDATION_ERROR`. Saving or selecting an expired card returns `400` with `PAYMENT_METHOD_EXPIRED`. Ownership and expiry are checked before changing defaults, and failed mutations preserve existing entries.

Using the `apiPost` helper below for a logged-in customer:

```javascript
const method = await apiPost('/api/account/payment-methods/add', {
  type: 'CREDIT', brand: 'visa', cardholderName: 'Alice Example',
  last4: '4242', expiryMonth: 12, expiryYear: 2030
});
await apiPost(`/api/account/payment-methods/default/${method.paymentMethodId}`);
const response = await fetch(`${API}/api/account/payment-methods?type=CREDIT`, {
  credentials: 'include'
});
console.log(await response.json());
// Add another active card before deleting the last active card.
await apiPost(`/api/account/payment-methods/delete/${method.paymentMethodId}`);
```

Mutations lock the customer row and run in a transaction. A PostgreSQL partial unique index permits at most one default per customer. Concurrent removals preserve the last active card. Saved entries reference the stable user ID, survive logout and email/password changes, and cascade on account deletion. Matching last four digits do not uniquely identify a card, so distinct saved entries are not deduplicated by those digits. No additional environment variables are required.

## Checkout and order creation

GET `/api/checkout` returns `{ cart, addressBook, savedPaymentMethods, paymentMethods, currency, ready }`. The nested cart, address book, and saved-card objects use the existing APIs' response shapes. `paymentMethods` contains `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, and `DIGITAL_WALLET`; `currency` is `USD`. `ready` is true when the cart is nonempty and valid and a shipping address exists. It indicates readiness to submit a pending order; a selected saved card is validated separately during creation.

Preview reads use a consistent PostgreSQL snapshot. They do not reserve stock, create orders, or clear the cart. Prices, visibility, formats, stock, address ownership, and any selected card are rechecked when placing the order.

POST `/api/orders` requires a customer session, a fresh `X-CSRF-TOKEN`, and an `Idempotency-Key` header. Example body:

```json
{
  "addressId": 1,
  "paymentMethod": "CREDIT_CARD",
  "savedPaymentMethodId": 2
}
```

`paymentMethod` is required. Matching ignores case, surrounding whitespace, and accepts spaces in place of underscores, such as `Bank Transfer`. `addressId` is optional: if omitted/null, the customer's default shipping address is used, falling back to the first saved address. A shipping address is required. `savedPaymentMethodId` is optional; when present it must be owned, unexpired, denominated in USD, and match `CREDIT_CARD`/`DEBIT_CARD`. Saved cards cannot be supplied for bank transfers or digital wallets. Card metadata is not a verified payment authorization.

The server calculates all quantities and prices from the current cart and locked catalog data. Client totals, items, currency, account IDs, order status, payment status, and raw card fields are rejected as unknown JSON fields. Totals use exact decimal arithmetic and represent the sum of order lines; shipping fees and tax calculations are not configured.

A successful new order returns `201`, a `Location: /api/orders/{orderId}` header, and the full order:

- `orderId`, `status: "pending"`, `paymentStatus: "unpaid"`, normalized `paymentMethod`, and `currency: "USD"`.
- `totalItems` (line count), `totalQuantity`, `totalPrice`, and `createdAt`.
- `address`: the shipping-address snapshot, including its original ID and shipping fields.
- `savedPaymentMethod`: selected masked card metadata, or null if no saved card was selected.
- `items`: each `orderItemId`, product snapshot (ID, name, category, images, attributes), selected attributes, quantity, unit price, and line total.

Creation locks the customer to serialize cart/address/card changes and competing requests for that account, then locks all cart products in ascending ID order and their categories. Stock is checked across all formats of each book and deducted once per product. Order headers/items, stock deduction, and cart clearing commit together. Any validation or database failure leaves stock and the cart unchanged and creates no partial order. Competing customers cannot purchase more than the available stock. Only the ordering customer's cart is cleared.

The idempotency key must contain 1–100 letters, digits, dots, underscores, colons, or hyphens and start with a letter or digit. Generate one key per order attempt and reuse it for retries. For the same customer and the same address/payment selections, a retry returns the original order with `200` and its `Location`, even after the cart is cleared or the original address/card is removed. It does not consume a newly filled cart. Reusing a key with different selections returns `409` with `IDEMPOTENCY_KEY_REUSED`. Keys are scoped to the customer; different customers can use the same key. Concurrent requests with different keys cannot consume the same cart twice.

GET `/api/orders/{id}` reads only the authenticated customer's order. Foreign and missing IDs both return `404` with `ORDER_NOT_FOUND`. Invalid IDs/headers/payment selections and missing shipping addresses return `400` with `INVALID_ORDER_REQUEST`; request-field validation returns `VALIDATION_ERROR`. Missing/foreign address and card selections use the existing `ADDRESS_NOT_FOUND`/`PAYMENT_METHOD_NOT_FOUND` responses. Creation rejects invalid carts with `409`: `EMPTY_CART`, `PRODUCT_UNAVAILABLE`, `INVALID_CART_FORMAT`, or `INSUFFICIENT_STOCK`. Database failures return a generic `500` with `DATABASE_ERROR`, without exposing SQL or order details.

Using the `apiPost` helper below:

```javascript
const previewResponse = await fetch(`${API}/api/checkout`, { credentials: 'include' });
const preview = await previewResponse.json();
if (!preview.ready) throw new Error('Check cart and shipping address');

const attemptKey = crypto.randomUUID(); // Retain this key if the request needs retrying.
const selections = {
  addressId: preview.addressBook.defaultAddressId,
  paymentMethod: 'BANK_TRANSFER'
};
const order = await apiPost('/api/orders', selections, { 'Idempotency-Key': attemptKey });
const orderResponse = await fetch(`${API}/api/orders/${order.orderId}`, { credentials: 'include' });
console.log(await orderResponse.json());
```

Snapshots preserve the order's shipping fields, masked payment metadata, product details, formats, and prices after catalog edits or deletion of the original address/card/product. Orders survive logout and email/password changes. These endpoints create pending, unpaid orders and deduct stock immediately. Admin order management below implements completion and cancellation with restocking. Payment-provider charging/refunds and shipping-provider integration remain separate features. The original frontend's snake_case order payload and checkout response need adaptation to this camelCase API contract.

## Customer order history

GET `/api/account/orders` lists the signed-in customer's orders with the same complete `OrderResponse` used by GET `/api/orders/{id}`. Each order contains its ID/date, current order and payment statuses, payment choice, USD total, shipping snapshot, optional masked saved-card snapshot, and ordered items with product details/images, selected formats, quantities, unit prices, and line totals. Items and totals describe the purchase at order creation, independent of later catalog prices, visibility, or deleted source records.

Query parameters:

| Parameter | Behavior |
| --- | --- |
| `page` | One-based page number, default `1`; positive integer up to `2147483647`. |
| `status` | Optional `pending`, `completed`, or `cancelled`; ignores case and surrounding whitespace. Omit to include all statuses. |

Response envelope:

```json
{
  "items": [],
  "page": 1,
  "pageSize": 15,
  "totalItems": 0,
  "totalPages": 0
}
```

`items` contains full orders when purchases exist. `totalItems` counts matching orders for this customer, while each order's own `totalItems` counts its item lines. Pages contain at most 15 orders, sorted by `createdAt` descending and then order ID descending. The count and all page contents use one consistent PostgreSQL snapshot per request. A new account or unmatched filter returns an empty page and zero totals. An out-of-range positive page returns an empty list while preserving the matching totals and requested page number. Invalid page/status values return `400` with `INVALID_ORDER_REQUEST`; `all` and payment states such as `paid` are not order-status filters.

History access requires a customer session; guests receive `401` and admins receive `403`. Ownership is derived from the authenticated account, so URL parameters cannot select another customer's history. History reads do not modify orders, carts, stock, or payment state. Full order details remain available at `/api/orders/{id}`, with identical `404` responses for missing and foreign orders. History survives logout and email/password changes because orders reference the stable user ID.

Example for a logged-in customer:

```javascript
const query = new URLSearchParams({ page: '1', status: 'completed' });
const response = await fetch(`${API}/api/account/orders?${query}`, {
  credentials: 'include'
});
if (!response.ok) throw new Error('Unable to load order history');
const history = await response.json();
console.log(history.items, history.totalPages);
```

Order items for each page are fetched in one batch. Existing owner/newest and order-item indexes support unfiltered history; V9 adds an owner/status/newest index for filtered queries. Existing orders are immediately included after the migration. The original frontend expects an unpaged snake_case array; its order service must consume `response.items`, map the camelCase order fields, and add pagination controls to display all pages.

## Admin authentication and authorization

Admin login accepts `email` and `password` at `/api/admin/auth/login`:

```json
{
  "email": "admin@example.com",
  "password": "your-admin-password"
}
```

Only database accounts with role `admin` can authenticate at this endpoint. Customer credentials are rejected with the same `401` error as unknown emails and incorrect passwords. Admin accounts use the admin login endpoint; the customer login endpoint continues to accept customers only.

Every `/api/admin/**` endpoint except POST `/api/admin/auth/login` requires `ROLE_ADMIN`. Unauthenticated requests receive `401`, while customers logged in with a password receive `403`. Remembered customers also remain blocked: Spring Security responds with `401` to prompt credential authentication when a remembered identity lacks the required role. Admin account creation also checks the role at the service layer. The database schema already contains the admin role, so existing databases need no new migration.

Admin login uses a browser-session cookie with a 30-minute idle timeout. It does not support Remember me. Switching from a customer login to an admin login revokes the previous customer's remembered-login tokens and clears the remember-me cookie. Use POST `/api/auth/logout` to log out either role.

An authenticated admin can create another admin at `/api/admin/auth/register`:

```json
{
  "username": "Second Admin",
  "email": "second-admin@example.com",
  "password": "a-long-admin-password",
  "confirmPassword": "a-long-admin-password",
  "phone": "0812345678"
}
```

Confirmation is required and must match the password. The remaining field limits match customer registration. The server assigns the admin role; request-supplied roles are rejected. Email uniqueness applies across both roles. Creating another admin does not switch the current session to the new account. Admin responses use the same public fields as customer responses, with `role: "admin"`.

### Create the first admin

Set these entries in the ignored `.env` file before the initial application start:

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=your-own-long-admin-password
```

Start with `docker compose up --build` (or export `.env` and use `./mvnw spring-boot:run`). The startup runner validates the fields and stores a BCrypt password hash. After the first successful start, set `BOOTSTRAP_ADMIN_ENABLED=false`, remove the bootstrap password, and restart. Subsequent admins are created by a logged-in admin through the protected endpoint.

The setup is idempotent: an existing admin email is left unchanged, including its password. An email already owned by a customer causes startup to fail; it never promotes that customer. No default admin account or password is included in the schema.

## Admin product and inventory management

All management endpoints require an admin login. Service checks bind the acting admin to the authenticated identity. POST, PUT, and DELETE requests require the session cookie and a fresh CSRF token. CORS supports these methods for the configured frontend origins. Customer/guest requests are rejected with `403`/`401`.

GET `/api/admin/products` and `/api/admin/inventory` share a paginated response `{ items, page, pageSize, totalItems, totalPages }`, with 15 products per page, newest first and ID descending to break timestamp ties. Each item contains `{ product, isVisible, categoryVisible, version, updatedAt }`; `product` uses the existing catalog's product response with actual `productQuantity`. Admin reads include hidden products and products in hidden categories. Count and page contents use one PostgreSQL snapshot.

Filters may be combined:

| Parameter | Behavior |
| --- | --- |
| `page` | Positive integer, default `1`, up to `2147483647`; out-of-range pages are empty with matching totals. |
| `q` | At most 255 characters; searches title, description, author, and SKU, case insensitively. `%`, `_`, and `!` are literal characters. |
| `categoryId` | Optional positive existing category ID, including a hidden category. |
| `visibility` | `all` (default), `visible`, or `hidden`; visibility combines product and category visibility. |
| `stock` | `all` (default), `out` (zero stock), or `low` (1 through the low-stock threshold). |
| `lowStockThreshold` | Integer from 0 to 100000, default `5`; used for low-stock filtering and inventory summary. |

GET `/api/admin/categories` returns category IDs/names/slugs/images, visibility, product counts, versions, and timestamps, including hidden categories. These categories provide the assignment options for products; creation/editing is documented below.

POST `/api/admin/products` example:

```json
{
  "categoryId": 1,
  "productName": "New book",
  "productDescription": "A book description",
  "productPrice": 14.25,
  "productQuantity": 20,
  "productImages": ["cover.png"],
  "productAttributes": {
    "author": "Author Name",
    "isbn": "9780000000000",
    "sku": "BOOK-001",
    "format": ["Paperback", "Hardcover"],
    "tags": ["fiction"]
  },
  "isVisible": true
}
```

Creation returns `201` and `Location: /api/admin/products/{id}`. Category, name, and price are required. Names are trimmed and limited to 255 characters; descriptions are limited to 20000 characters. Prices must be nonnegative, with at most eight integer digits and two decimal places. Quantities must be whole nonnegative 32-bit integers. Omitted images/attributes default to `[]`/`{}`, stock to zero, and visibility to true. New products start at version zero. Hidden categories may be assigned, but their products remain hidden from the public catalog.

Images are an array of up to ten nonblank filename references, at most 200 characters each, using letters, digits, dots, underscores, and hyphens and starting with a letter/digit. Attributes must be a JSON object, at most 50 fields and 16 KiB. Author/ISBN/SKU are trimmed strings limited to 255/32/100 characters. Nonblank SKUs are unique ignoring case and surrounding whitespace; a blank SKU is removed. `format` is a nonblank string or an array of up to ten distinct nonblank strings (50 characters each), normalized for whitespace. An empty format array means no format selection. Optional `discount` is a number from 0 to 100 with at most two decimal places; it is descriptive metadata and does not change the submitted catalog price. Additional metadata such as tags can be stored within the object limit. Stock is authoritative in `productQuantity`; duplicated stock/quantity attributes are rejected.

PUT `/api/admin/products/{id}` requires category, name, price, and `expectedVersion`. It replaces these fields and the description (omitted/null clears the description). Omitted/null images, attributes, quantity, and visibility preserve their existing values; explicit `[]`/`{}` clears images/attributes. Quantity changes in this request are recorded as `Product edit`. Every successful metadata edit advances the version.

Dedicated stock update:

```json
{
  "quantity": 25,
  "expectedVersion": 0,
  "reason": "New delivery"
}
```

PUT `/api/admin/products/{id}/stock` requires an absolute quantity, the current version, and a nonblank reason of up to 500 characters. Stock and its adjustment record commit together; storage failure rolls back quantity and version. History records initial stock, stock changes in product edits, and dedicated admin stock corrections, including the authenticated admin ID, previous/new quantities, generated delta, reason, and timestamp. GET `/api/admin/products/{id}/stock-history?page=1` returns a page of these records, newest first. Cancellation restorations also appear in this history with their linked `orderId`; other adjustments have `orderId: null`. Checkout deductions advance the version but are represented by orders rather than this admin-adjustment history.

PUT `/api/admin/products/{id}/visibility` accepts `{ "isVisible": false, "expectedVersion": 0 }`. Setting the same quantity/visibility is a no-op after checking the version. A real mutation advances the version and update timestamp. Hiding products prevents public retrieval and marks existing cart/wishlist entries unavailable; it preserves stock and order history.

Read the current version before editing, correcting stock, changing visibility, or deleting. Mutations lock the product and reject stale `expectedVersion` with `409` and `STALE_PRODUCT_VERSION`. Checkout also advances this version when it deducts stock, so stale admin forms cannot restore previously sold copies. Concurrent editors cannot overwrite each other. Reload after a conflict and review the current values before submitting a new edit.

DELETE `/api/admin/products/{id}?expectedVersion={version}` returns `204`. PostgreSQL removes current cart/wishlist entries and this product's admin adjustment records, clears live product references from order items, and retains their product/format/price snapshots. Uploaded images are retained so past orders can still display their covers. Missing products/categories return `404` with `ADMIN_PRODUCT_NOT_FOUND`/`ADMIN_CATEGORY_NOT_FOUND`. Invalid IDs, filters, attributes, or missing versions return `400` with `INVALID_ADMIN_PRODUCT_REQUEST`; field validation uses `VALIDATION_ERROR`. Duplicate SKUs return `409` with `SKU_IN_USE`.

GET `/api/admin/inventory/summary?lowStockThreshold=5` returns `{ totalProducts, totalUnits, outOfStockProducts, lowStockProducts, visibleProducts, hiddenProducts, lowStockThreshold }`. Counts include hidden inventory. Low-stock counts exclude zero-stock products; totals use PostgreSQL aggregates and 64-bit API values.

### Cover images

POST `/api/admin/product-images` uploads one multipart part named `file`. Supply the CSRF token in the header and let the browser set the multipart content type. PNG/JPEG images must be at most 5 MiB, 4096 pixels on each side, and ten million pixels in total. The backend decodes and re-encodes the image, strips original metadata/trailing payloads, and generates a UUID filename. It returns `201` with `{ "image": "uuid.png", "url": "/api/product-images/uuid.png" }` and a `Location` header. Store `image` in `productImages`, and use `url` to display that uploaded cover. Existing catalog image filenames continue to refer to frontend assets.

```javascript
const csrfResponse = await fetch(`${API}/api/auth/csrf`, { credentials: 'include' });
const csrf = await csrfResponse.json();
const form = new FormData();
form.append('file', selectedFile);
const response = await fetch(`${API}/api/admin/product-images`, {
  method: 'POST', credentials: 'include', headers: { [csrf.headerName]: csrf.token }, body: form
});
if (!response.ok) throw new Error('Unable to upload cover');
const cover = await response.json();
const product = await apiPost('/api/admin/products', {
  categoryId: 1, productName: 'New book', productPrice: 14.25,
  productImages: [cover.image], productQuantity: 20,
  productAttributes: { format: ['Paperback'] }
});
```

Invalid images return `400` with `INVALID_PRODUCT_IMAGE`; oversized uploads return `413` with `PRODUCT_IMAGE_TOO_LARGE`. Missing image names return `404`; storage failures return `503` with `IMAGE_STORAGE_UNAVAILABLE`. Uploaded images are public, served with their detected content type, nosniff, and immutable caching. Original filenames never control the storage path.

Local runs use `PRODUCT_IMAGE_DIRECTORY` (default `./uploads/product-images`); preserve this directory alongside the database. Docker Compose uses the persistent `bookstore_images` volume, owned by the application's nonroot user. Cover uploads and product saves are separate requests; uploading without saving a product can leave an unused image. Retained/unused image cleanup is an operator task. Uploaded files are excluded from Git and Docker build context. The original frontend needs to adapt its multipart product submissions to image upload followed by this camelCase JSON contract and include versions when editing/deleting.

## Admin category management

Category management uses the existing admin login and role checks. Every POST, PUT, and DELETE requires the session cookie and a fresh CSRF token. Service authorization also binds the supplied acting admin to the authenticated identity. Guests receive `401`; customer accounts receive `403`.

GET `/api/admin/categories` preserves the existing unpaged array response and sorts by category ID ascending. It includes hidden categories and all products in each count, including hidden/zero-stock products. Optional `q` (up to 255 characters) searches names/slugs case insensitively, with literal `%`, `_`, and `!` handling. Optional `visibility` is `all` (default), `visible`, or `hidden`, ignoring case and surrounding whitespace. GET `/api/admin/categories/{id}` returns one category by positive numeric ID.

POST `/api/admin/categories` example:

```json
{
  "categoryName": "Historical Fiction",
  "slug": "historical-fiction",
  "categoryImage": "history.png",
  "isVisible": true
}
```

Only `categoryName` is required. Names are trimmed and limited to 100 characters. A provided slug is trimmed, lowercased, and must contain ASCII letters/digits separated by single hyphens, include at least one letter, and be at most 120 characters. If omitted/null on creation, the slug is generated from the name by removing accent marks and replacing punctuation/whitespace with hyphens. Generated slugs containing only digits/hyphens receive a `category-` prefix; names without ASCII letters/digits receive a `category-` UUID slug. Slugs are unique in PostgreSQL. Category names can repeat when different explicit slugs are supplied.

`categoryImage` is an optional filename reference, up to 500 characters, using letters, digits, dots, underscores, and hyphens and starting with a letter/digit. It can reference an existing frontend asset or an image already uploaded through `/api/admin/product-images`. Omitted/null/blank images default to null. Visibility defaults to true. Unknown JSON fields, including IDs, product counts, version counters, and role/account fields, are rejected.

Creation returns `201` with `Location: /api/admin/categories/{id}` and a category response:

```json
{
  "categoryId": 6,
  "categoryName": "Historical Fiction",
  "slug": "historical-fiction",
  "categoryImage": "history.png",
  "isVisible": true,
  "productCount": 0,
  "version": 0,
  "createdAt": "2026-10-15T00:00:00Z",
  "updatedAt": "2026-10-15T00:00:00Z"
}
```

PUT `/api/admin/categories/{id}` requires `categoryName` and `expectedVersion`. Omitted/null slug, image, and visibility preserve their current values. Send an empty/blank image string to clear it. Renaming preserves the category's URL unless an explicit replacement slug is supplied. Changing a slug makes the new public URL available and the previous slug unavailable. Every successful metadata edit advances the category's version and update timestamp.

PUT `/api/admin/categories/{id}/visibility` accepts `{ "isVisible": false, "expectedVersion": 0 }`. Setting the current visibility is a no-op after the version check. PUT `/api/admin/categories/{id}/toggle` accepts `{ "expectedVersion": 0 }` and flips visibility, advancing the version. Repeating the same toggle request returns a stale-version conflict instead of flipping it again.

Hidden categories and their products disappear from public listings/search/retrieval. Existing cart/wishlist items remain saved with `available: false`; checkout rejects them. Unhiding a category restores access to products whose individual visibility is still true. Category changes do not modify product visibility, stock, or saved order snapshots. Checkout holds category locks while creating an order, so hiding and checkout serialize consistently.

DELETE `/api/admin/categories/{id}?expectedVersion={version}` returns `204` for an empty category. It locks the category and rechecks product references before deletion. Nonempty categories return `409` with `CATEGORY_NOT_EMPTY`; move or remove their products first. PostgreSQL's restricted product/category foreign key and assignment locks preserve referential integrity during concurrent product creation/reassignment. Deletion does not cascade to products or delete referenced image files.

All mutations require the current nonnegative `expectedVersion`. A stale edit, visibility request, toggle, or deletion returns `409` with `STALE_CATEGORY_VERSION`; reload before retrying. Duplicate generated/explicit slugs return `409` with `CATEGORY_SLUG_IN_USE`, and failed updates roll back all changes. Missing categories return `404` with `ADMIN_CATEGORY_NOT_FOUND`. Invalid IDs, filters, image references, slugs, or missing versions return `400` with `INVALID_ADMIN_CATEGORY_REQUEST`; request-field validation uses `VALIDATION_ERROR`.

Example using the `apiPost` helper below after admin login:

```javascript
const category = await apiPost('/api/admin/categories', { categoryName: 'Historical Fiction' });
const response = await fetch(`${API}/api/admin/categories/${category.categoryId}`, {
  credentials: 'include'
});
console.log(await response.json());
```

For PUT/DELETE, fetch a fresh CSRF token and send it with the method, credentials, and current version. The original frontend must map snake_case fields to this camelCase contract and include `expectedVersion` when editing, toggling, or deleting. No additional environment variables are required.

## Admin order management

All `/api/admin/orders` endpoints require an admin session. Guests receive `401`, customers receive `403`, and PUT also requires a fresh `X-CSRF-TOKEN`. Service authorization binds operations to the authenticated admin identity; request JSON cannot choose an actor, owner, payment status, stock flag, item list, or total.

GET `/api/admin/orders?page=1&status=pending&q=alice&customerId=42` returns `{ items, page, pageSize, totalItems, totalPages }`. Filters are optional and combine when present. `status` accepts `pending`, `completed`, or `cancelled`, ignoring case and surrounding whitespace. `customerId` must be a positive 64-bit integer. `q` searches order ID, current customer name, and email, case-insensitively, with a maximum of 255 characters. SQL syntax and wildcard characters are treated as literal text. Omit `status` for all statuses.

Each page contains at most 15 orders, newest creation date first and then highest order ID. `totalItems` counts matching orders across all customers. Count, headers, and batched item retrieval use one consistent PostgreSQL snapshot. Empty/unmatched searches return zero totals; positive pages beyond the last page return empty `items` with the matching totals. Page numbers must be positive 32-bit integers.

Each entry and GET `/api/admin/orders/{id}` return `{ order, customer, version, stockReleased, updatedAt }`. The nested `order` is the existing customer `OrderResponse`, including all item lines and immutable product/format/price, shipping, and optional masked payment snapshots. `customer` contains the account's current `userId`, `username`, `email`, and nullable `phone`. Passwords, hashes, idempotency keys, and request fingerprints are excluded. Existing customer APIs keep their response shapes and ownership restrictions.

PUT `/api/admin/orders/123/status` accepts:

```json
{ "status": "cancelled", "expectedVersion": 0, "reason": "Customer requested cancellation" }
```

`expectedVersion` is required and nonnegative; `reason` must be nonblank and at most 500 characters. A pending order may become `completed` or `cancelled`. Completed and cancelled orders are final: reopening or changing between them returns `409` with `INVALID_ORDER_STATUS_TRANSITION`. A real change advances the order version and update timestamp, records the acting admin and trimmed reason, and returns the full updated admin order with `200`. A matching-version request for the current status is a no-op. A stale version returns `409` with `STALE_ORDER_VERSION`, including stale retries for a status already applied; reload the order before retrying.

Completion records fulfillment without changing stock or payment status. Cancellation aggregates quantities across all formats of each live product, restores that stock once, advances affected product versions, and records inventory adjustments linked by `orderId`. The order header is locked to serialize competing admin updates, and products are locked in ascending ID order, matching checkout. Order status/version, stock restoration, inventory records, and status history commit together. Database failures roll back everything. Restoring beyond the maximum 32-bit stock count returns `409` with `STOCK_RESTORE_OVERFLOW` and leaves all products and the order unchanged. Stale admin inventory forms cannot overwrite restored stock.

Deleted source products are skipped using the live nullable product references; snapshots remain intact and products are never recreated from them. `stockReleased: true` means cancellation restoration was processed, including when all source products have been deleted. Cancellation leaves the customer's cart cleared and preserves historical items, totals, and shipping/payment snapshots. Payment status remains unchanged for both completion and cancellation; these actions do not charge or refund a payment.

GET `/api/admin/orders/{id}/status-history?page=1` returns `{ items, page, pageSize, totalItems, totalPages }`, newest changes first. Each entry includes `changeId`, `orderId`, nullable `adminUserId`, `previousStatus`, `newStatus`, `orderVersion`, `reason`, and `createdAt`. Deleting an admin clears the actor reference while preserving the audit. No-op/stale/failed updates do not add history.

Missing orders return `404` with `ADMIN_ORDER_NOT_FOUND`. Invalid IDs, pages, filters, or status values return `400` with `INVALID_ADMIN_ORDER_REQUEST`; invalid request fields use `VALIDATION_ERROR` or `INVALID_JSON`. No new configuration is required. The original frontend must consume `response.items`, map the nested camelCase order/customer fields, and include the displayed `version` as `expectedVersion` plus a reason when changing status.

## Admin customer reporting

GET `/api/admin/customers` returns `{ items, page, pageSize, totalItems, totalPages }`, with up to 15 customers per page. Admin accounts are excluded. A customer with no orders is included with zero metrics and `lastOrderAt: null`. All reporting endpoints require an admin session; guests receive `401`, customers receive `403`, and service authorization also checks the authenticated admin identity. These GET endpoints do not require a CSRF token and do not modify accounts, orders, payments, carts, or stock.

Query parameters:

| Parameter | Meaning |
| --- | --- |
| `page` | Positive 32-bit page number, default `1`. Pages beyond the last page retain totals and return empty `items`. |
| `q` | Case-insensitive substring search of customer name/email, up to 255 characters. Surrounding whitespace is ignored; `%`, `_`, `!`, and SQL text are literal. |
| `activity` | `all` (default), `ordered` (at least one order, including cancelled orders), or `no-orders`. |
| `sort` | `newest` (default), `orders` (highest order count), or `spent` (highest recorded paid total). Ties use newest customer creation date, then highest user ID. |

`activity` and `sort` ignore case and surrounding whitespace. Filters combine. For example, `/api/admin/customers?q=alice&activity=ordered&sort=spent&page=1` searches matching customers who have placed an order.

Each item and GET `/api/admin/customers/{id}` include `userId`, `username`, `email`, nullable `phone`, `createdAt`, `updatedAt`, `orderCount`, `pendingOrderCount`, `completedOrderCount`, `cancelledOrderCount`, `orderValue`, `totalSpent`, `currency: "USD"`, and nullable `lastOrderAt`. Contact fields describe the current account; order metrics use stored order headers and historical prices. Passwords/hashes, roles, sessions, login/reset tokens, shipping addresses, and saved payment details are excluded.

Metrics cover the customer's full order history:

- `orderCount` includes all order statuses; the three status counts add up to it. `lastOrderAt` is the newest order creation timestamp, including cancelled orders.
- `orderValue` sums historical totals of pending/completed orders, excluding cancelled orders, regardless of payment status. It describes order value and does not imply that money was received.
- `totalSpent` sums orders whose current `paymentStatus` is `paid`. Unpaid and refunded orders contribute zero. A paid cancelled order remains included until its payment is recorded as refunded, because admin cancellation itself does not issue a refund. New checkout orders are unpaid and initially contribute zero.

All monetary aggregation uses PostgreSQL `NUMERIC` and Java `BigDecimal`. Totals come from order headers, so an order with multiple items is counted once. Reports reflect committed checkout, status/payment changes, and profile updates without a separate refresh/import job. Product/address/card deletion does not change historical totals. Each list's count and contents use one consistent PostgreSQL snapshot, including when customers register or orders commit during the request.

GET `/api/admin/customers/summary?q=alice&activity=ordered` returns `totalCustomers`, `customersWithOrders`, `customersWithoutOrders`, `totalOrders`, `pendingOrders`, `completedOrders`, `cancelledOrders`, `orderValue`, `totalSpent`, and `currency`. The same `q` and `activity` filters apply to the whole matching customer group, independently of list pagination. Empty matches return zero counts and amounts. Use `/api/admin/orders?customerId={userId}` for the selected customer's full paginated order details through the existing admin order API.

Invalid IDs, pages, search lengths, activity filters, or sorts return `400` with `INVALID_ADMIN_CUSTOMER_REQUEST`. Missing customer IDs and admin account IDs both return `404` with `ADMIN_CUSTOMER_NOT_FOUND`. No new environment variables are required. The original frontend must consume `response.items`, map `user_id`, `order_count`, and `total_spent` to `userId`, `orderCount`, and `totalSpent`, and add pagination/search controls for the full report. Its hardcoded Active badge is not an account-state field provided by this API.

## Admin dashboard

GET `/api/admin/dashboard?days=30` returns the following fields. An admin session is required; guests receive `401`, customers receive `403`, and service authorization also binds the request to the authenticated admin identity. This GET needs no CSRF token and does not modify store data.

| Field | Meaning |
| --- | --- |
| `totalProducts` | All current products, including products hidden individually or through their categories. |
| `totalCustomers` | Current customer accounts, excluding admins. |
| `totalOrders` | All lifetime orders belonging to current customer accounts, including cancelled orders. |
| `totalRevenue` | Sum of order totals currently recorded as paid, in USD, matching customer reporting's `totalSpent`. |
| `orderValue` | Sum of lifetime pending/completed order totals, independent of payment status. |
| `pendingOrders`, `completedOrders`, `cancelledOrders` | Lifetime customer-order counts by current status; these add up to `totalOrders`. |
| `customersWithOrders`, `customersWithoutOrders` | Lifetime order participation, including cancelled orders. |
| `currency` | `USD`. Monetary amounts use PostgreSQL `NUMERIC` and Java `BigDecimal`. |
| `inventory` | Existing inventory summary: total products/units, out-of-stock and low-stock counts, effective visible/hidden counts, and `lowStockThreshold: 5`. Low stock means 1–5 units; zero stock is counted separately. |
| `recentOrders` | Up to 10 customer orders, newest creation timestamp first, then highest order ID. |
| `salesTrend` | Consecutive daily customer-order cohorts for the requested UTC date window, including zero days. |
| `generatedAt` | PostgreSQL transaction timestamp in UTC. All report sections use the same consistent database snapshot. |

Revenue excludes unpaid/refunded orders. A paid cancelled order remains in `totalRevenue` until its payment status is recorded as refunded, since cancellation does not itself refund a payment. New checkout orders are unpaid, so they increase order counts/value while contributing zero revenue. Headers supply all counts and totals; multiple order-item lines do not multiply them.

Each recent order includes `orderId`, `userId`, current `username`, `status`, `paymentStatus`, `paymentMethod`, `totalItems`, `totalQuantity`, `totalPrice`, `currency`, and `createdAt`. This is a compact summary for the dashboard table. Use `/api/admin/orders/{orderId}` for full details and immutable snapshots. Customer contact details, shipping/card snapshots, credentials, and idempotency keys are excluded from dashboard entries.

`days` must be a whole number between 1 and 366; omitting it uses 30. It controls only the trend window, leaving lifetime cards and recent orders unchanged. `salesTrend` contains `days`, inclusive `startDate`/`endDate`, `timeZone: "UTC"`, window `totalOrders`, `orderValue`, `totalRevenue`, and `items`. The end date is the UTC day containing `generatedAt`; the start date is `days - 1` days earlier. Every date appears once in ascending order, with zeros for missing dates. Orders before the window or on later UTC dates are excluded from the trend.

Each daily item contains `date`, `totalOrders`, `pendingOrders`, `completedOrders`, `cancelledOrders`, `orderValue`, and `totalRevenue`, using the same status/payment rules as the lifetime cards. These are cohorts grouped by **order creation date**, reflecting each order's current status/payment state. Payment/refund timestamps are not stored by the existing payment model, so the trend does not represent money collected or refunded on each calendar day.

Changes from checkout, admin order management, inventory/category edits, recorded refunds, and customer profile updates appear on the next request without refreshing a report table. Original order prices remain the source of historical values after product deletion. Empty stores return zero cards, empty recent orders, and a complete zero-filled trend. Invalid `days` values return `400` with `INVALID_ADMIN_DASHBOARD_REQUEST`; storage failures return the existing generic `500`/`DATABASE_ERROR` response. No new environment setting is required.

The original frontend can use `totalOrders`, `totalRevenue`, `totalCustomers`, and `totalProducts` directly. Its recent-order fields need mapping from `order_id`, `total_price`, and `created_at` to `orderId`, `totalPrice`, and `createdAt`; `username` and `status` retain their names. The stock alerts and daily trend are additional JSON data for dashboard widgets.

## Profile management

Customers use `/api/account/profile` and `/api/account/password`; admins use `/api/admin/profile` and `/api/admin/password`. These endpoints operate only on the authenticated identity. There is no request-supplied target account ID or role. Role checks protect both the endpoints and the service layer, and API responses exclude passwords and hashes.

GET on the profile endpoint returns the same public account fields as `/api/auth/me` or `/api/admin/auth/me`. POST on the profile endpoint accepts:

```json
{
  "username": "Alice Updated",
  "email": "alice@example.com",
  "phone": "0899999999"
}
```

Username is required, trimmed, and limited to 100 characters. Email is required, validated, lowercased, and limited to 255 characters. Phone is optional and limited to 20 characters; omitting it or sending an empty string clears the saved phone number. This POST replaces these profile fields rather than applying a partial update. Unknown fields, including `role` and `userId`, return `400`.

A successful update returns:

```json
{
  "user": {
    "userId": 1,
    "username": "Alice Updated",
    "email": "alice@example.com",
    "phone": "0899999999",
    "role": "customer"
  },
  "reauthenticationRequired": false
}
```

Name and phone updates keep the current login and remembered-login state. Changing only the email's letter case also keeps the login because emails are normalized. Changing to a different email returns `reauthenticationRequired: true`, signs the current browser out, and revokes all of the account's previous sessions, remembered-login tokens, and pending reset links. Log in again with the new email and the existing password. The account ID and role remain unchanged.

Email uniqueness applies across customers and admins. A conflicting email returns `409` with code `EMAIL_IN_USE`. The transaction rolls back all changes, including session/token revocation, so the existing account and login remain usable.

POST on the password endpoint accepts:

```json
{
  "currentPassword": "your-current-password",
  "newPassword": "your-new-long-password",
  "confirmPassword": "your-new-long-password"
}
```

The current password must match the stored BCrypt hash. The new password must be confirmed, contain at least 8 characters, and fit within the 72 UTF-8 byte limit. An incorrect current password returns `400` with code `INCORRECT_CURRENT_PASSWORD`; validation failures return `400` with code `VALIDATION_ERROR`. Both leave the password, login sessions, and reset links unchanged.

A successful password change returns `200` with `{ "message": "Password changed successfully. Please log in." }`. It updates the hash and timestamp and revokes all of the account's sessions, remembered-login tokens, and pending reset links in one transaction. The current browser is signed out; log in again with the new password. Other accounts remain logged in. All profile and password POST requests require a CSRF token and cookie, using the `apiPost` helper below.

Profile management uses the existing `users` columns and authentication tables, so no additional migration or environment setting is required.

## Password recovery

Both customer and admin accounts use the same recovery endpoints. Send POST `/api/auth/forgot-password` with:

```json
{ "email": "alice@example.com" }
```

The response is `200` with the same generic `message` for registered and unknown emails. No account details are returned. In SMTP mode, the link is sent to the normalized account email and is excluded from the API response. In local demo mode, a registered account's response also contains `resetLink`, matching the original project's simulated-email behavior.

Open GET `/api/auth/reset-password/{token}` to check a link. It returns JSON, not an HTML form; the frontend can use this check before showing its new-password form. Viewing a valid link does not consume it. Submit the new password to POST on the same endpoint:

```json
{
  "newPassword": "a-new-long-password",
  "confirmPassword": "a-new-long-password"
}
```

Passwords must match, contain at least 8 characters, and fit within BCrypt's 72 UTF-8 byte limit. Validation or CSRF failures leave the link usable. Unknown, malformed, expired, previously replaced, and consumed tokens return `400` with code `INVALID_RESET_TOKEN`. A successful reset returns `200` with `{ "message": "Password reset successfully. Please log in." }`; it does not automatically log the user in or change their role.

Tokens contain 32 random bytes and expire after 30 minutes. PostgreSQL stores only their SHA-256 hashes. Each account has at most one reset token; a new request replaces the previous link. Account row locks and transactional token consumption ensure concurrent attempts cannot reset a password twice with the same link. Updating the password, consuming the token, and revoking all of that account's sessions and remembered-login tokens occur in one transaction. Other accounts remain logged in.

All recovery POST requests require the CSRF token and cookie obtained from GET `/api/auth/csrf`, even when the user is logged out. Use `apiPost` below for these requests as well.

### Delivery setup

Add the appropriate settings to your existing `.env` and restart the backend. Recovery delivery defaults to `disabled` when no mode is configured; forgot-password requests then return `503` with code `RESET_DELIVERY_UNAVAILABLE`. `.env.example` explicitly selects `demo` for local development.

For the original simulated-email flow:

```dotenv
PASSWORD_RESET_DELIVERY=demo
PASSWORD_RESET_LINK_BASE_URL=http://localhost:8080/api/auth/reset-password
```

Demo mode exposes usable reset links in responses and is intended only for local development. For email delivery:

```dotenv
PASSWORD_RESET_DELIVERY=smtp
PASSWORD_RESET_LINK_BASE_URL=https://your-frontend.example/reset-password
SMTP_HOST=smtp.your-provider.example
SMTP_PORT=587
SMTP_USERNAME=your-smtp-username
SMTP_PASSWORD=your-smtp-password
SMTP_FROM=noreply@your-domain.example
SMTP_AUTH=true
SMTP_STARTTLS=true
```

The link base must be an absolute HTTP(S) URL without credentials, query parameters, or fragments. The backend appends `/{token}`. Point it at your frontend's password-reset route once that form is implemented. The default points to the API's JSON link-check endpoint. SMTP uses a fixed configured URL, so request Host headers cannot alter emailed reset links.

SMTP connections and reads/writes have five-second timeouts. Missing SMTP configuration or a delivery failure returns the generic `503` delivery error. A failed delivery rolls back token replacement, preserving any preceding valid link. No live SMTP credentials are bundled.

## Frontend integration

Use `credentials: 'include'` for every request, including the initial CSRF fetch. Obtain a fresh CSRF token after login or logout because authentication invalidates the previous token. This example fetches a token before each POST:

```javascript
const API = 'http://localhost:8080';

async function apiPost(path, body = {}, extraHeaders = {}) {
  const csrfResponse = await fetch(`${API}/api/auth/csrf`, {
    credentials: 'include'
  });
  if (!csrfResponse.ok) throw new Error('Unable to obtain CSRF token');
  const csrf = await csrfResponse.json();

  const response = await fetch(`${API}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      ...extraHeaders,
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token
    },
    body: JSON.stringify(body)
  });
  const result = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(result.message);
  return result;
}

await apiPost('/api/auth/register', {
  username: 'Alice', email: 'alice@example.com', password: 'a-long-customer-password'
});
await apiPost('/api/auth/login', {
  email: 'alice@example.com', password: 'a-long-customer-password', rememberMe: true
});
const me = await fetch(`${API}/api/auth/me`, { credentials: 'include' });
console.log(await me.json());
await apiPost('/api/auth/logout');

// With the first admin already created:
await apiPost('/api/admin/auth/login', {
  email: 'admin@example.com', password: 'your-admin-password'
});
await apiPost('/api/admin/auth/register', {
  username: 'Second Admin', email: 'second-admin@example.com',
  password: 'a-long-admin-password', confirmPassword: 'a-long-admin-password'
});
await apiPost('/api/auth/logout');
```

## Database

`src/main/resources/db/migration/V1__customer_authentication.sql` creates:

- `users`: identity primary key, unique normalized email, BCrypt hash, contact information, role, and timestamps.
- `persistent_logins`: persistent-login series, rotating token, last-use timestamp, and a foreign key to the customer's email.
- `spring_session` and `spring_session_attributes`: database-backed sessions, expiry indexes, and cascading attribute cleanup.

`V2__password_recovery.sql` adds `password_resets`: a unique account reference, unique SHA-256 token hash, expiry timestamp, and creation timestamp. It upgrades existing databases through Flyway without modifying the first migration. Deleting an account cascades to its reset token.

`V3__product_catalog.sql` adds `categories` and `products`. Category slugs are unique. Products reference categories with restricted deletion, use `NUMERIC(10,2)` prices, nonnegative stock, and `JSONB` images/attributes. Indexes support category lookup and newest-visible-product listings. The optional startup fixture is stored separately in `db/demo/catalog.sql`.

`V4__wishlists.sql` adds `wishlist`, with foreign keys to users and products, cascading deletion, a unique user/product constraint, and indexes for listing by owner/save time and cleaning up deleted products.

`V5__shopping_cart.sql` adds `cart_items`, with user/product foreign keys, cascading deletion, a 1–10 quantity constraint, JSONB selected attributes, a unique customer/product/normalized-format index, and a product index.

`V6__address_book.sql` adds `addresses`, with a cascading user foreign key, field limits/defaults, an index for newest addresses per owner, and a partial unique index for one default per customer. Flyway applies this migration automatically on startup.

`V7__saved_payment_methods.sql` adds `payment_methods`: a cascading user foreign key, masked display fields, validated card types/brands/expiry ranges, default USD currency, creation timestamp, a newest-per-owner index, and a partial unique index for one default per customer. Flyway applies it to existing databases on startup.

`V8__checkout_and_orders.sql` adds `orders` and `order_items`. Orders reference users, optionally retain source address/card references with `ON DELETE SET NULL`, and store immutable JSONB shipping/payment snapshots, USD decimal totals, statuses, a SHA-256 request fingerprint, and a unique customer/idempotency key. Order items cascade with their order, retain JSONB product/format snapshots, permit source product deletion, enforce quantity/price limits, and generate line totals from quantity × unit price. Owner/newest and foreign-key indexes support lookups. Flyway applies V8 automatically without changing earlier migrations.

`V9__customer_order_history.sql` adds `orders_user_status_newest_idx` on `(user_id, status, created_at DESC, order_id DESC)`. It supports customer-scoped, status-filtered history without changing existing order data or earlier migrations.

`V10__admin_product_inventory.sql` adds product version counters, a case-insensitive nonblank SKU unique index, admin newest/stock indexes, and `inventory_adjustments`. Adjustment rows reference products (cascade deletion) and the acting admin (set null on account deletion), enforce nonnegative counts/nonblank reasons, generate signed deltas, and index newest changes per product. Existing products begin at version zero. Existing duplicate nonblank SKUs must be resolved before applying the unique index.

`V11__admin_category_management.sql` adds category version counters and update timestamps. Existing categories start at version zero, retain their IDs/slugs/names/images/visibility/creation dates, and receive an initial update timestamp at migration. The existing unique slug constraint and restricted product foreign key remain in effect.

`V12__admin_order_management.sql` adds order versions/update timestamps, a stock-restoration marker constrained to cancelled orders, global/status newest indexes, and `order_status_changes` with actor references, reasons, and unique per-order change versions. Inventory adjustments gain an optional order reference that clears on order deletion. Existing order statuses, payment states, totals, item/shipping/payment snapshots, and stock are preserved. Existing cancelled orders are not automatically restocked; their new marker remains false. New pending-order cancellations process restoration atomically and set it true.

`V13__admin_customer_reporting.sql` adds a partial customer/newest index and the live `admin_customer_order_statistics` view. The view aggregates existing order headers by customer, with status counts, non-cancelled order values, paid totals, and latest order timestamps. The reporting repository left-joins it to customers so accounts with no purchases remain visible. Existing customer/order data and earlier migration checksums remain unchanged; no report rows or credentials are copied into a new table.

`V14__admin_dashboard.sql` adds an index on order creation dates in UTC and the live `admin_dashboard_daily_sales` view. The view aggregates customer order headers by UTC creation date, with status counts, non-cancelled values, and currently paid totals. Existing data and migration checksums are preserved. The dashboard also reuses the existing customer and inventory summary services inside one read-only transaction.

`V15__faq_retrieval.sql` creates `faqs` with generated IDs, constrained question/answer text, nonnegative display order, visibility, and creation/update timestamps. A partial `(sort_order, faq_id)` index supports ordered visible retrieval. Existing customer/catalog/order data and earlier migration checksums remain unchanged. Optional FAQ samples are imported separately only when explicitly enabled.

Flyway's `out-of-order` setting allows an existing address-branch database that already applied V6 to apply the merged cart migration V5. Fresh databases apply V1–V15 in order. Original migration files and checksums remain unchanged; existing accounts and addresses are retained without resetting the database.

All account queries are parameterized. Registration relies on PostgreSQL's unique constraint, including concurrent registration attempts. Passwords and password hashes are excluded from API responses. Customer and admin accounts share `users`, with separate role-filtered authentication providers and role checks on protected endpoints.

## Configuration

| Variable | Default / requirement |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bookstore` |
| `DB_USERNAME` | `bookstore` |
| `DB_PASSWORD` | Required |
| `REMEMBER_ME_KEY` | Required stable secret, at least 32 characters |
| `COOKIE_SECURE` | `false` for local HTTP; set `true` when serving over HTTPS |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` |
| `PORT` | `8080` for local runs |
| `CATALOG_DEMO_ENABLED` | `false`; enable to import the original catalog when both catalog tables are empty |
| `FAQ_DEMO_ENABLED` | `false`; enable to import five original frontend sample FAQs when the FAQ table is empty |
| `PRODUCT_IMAGE_DIRECTORY` | `./uploads/product-images` for local runs; Docker Compose mounts persistent image storage at `/app/uploads/product-images` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false`; enable explicitly to provision the initial admin |
| `BOOTSTRAP_ADMIN_USERNAME` | `admin` |
| `BOOTSTRAP_ADMIN_EMAIL` | Required when bootstrap is enabled |
| `BOOTSTRAP_ADMIN_PASSWORD` | Required when bootstrap is enabled; same password limits as registration |
| `PASSWORD_RESET_DELIVERY` | `disabled`; choose `demo` for local links or `smtp` for email |
| `PASSWORD_RESET_LINK_BASE_URL` | `http://localhost:8080/api/auth/reset-password`; reset links append `/{token}` |
| `SMTP_HOST` | Required for SMTP mode |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP credentials |
| `SMTP_FROM` | `noreply@bookstore.local`; configure a sender accepted by your provider |
| `SMTP_AUTH`, `SMTP_STARTTLS` | `true`; STARTTLS is required when enabled |

Cookies use SameSite=Lax; deploy the frontend and API on the same site. CORS allows only the configured origins and includes credentials. Keep the remember-me key stable across restarts. PostgreSQL session storage avoids losing active sessions when the application restarts.

## Verify

With Docker running:

```sh
./mvnw verify
```

Tests start an isolated PostgreSQL 17 container and the real HTTP server. They verify customer and admin password authentication, email uniqueness across roles, validation, role injection rejection, database session persistence, session ID rotation, CSRF enforcement, CORS, remembered login, expired/tampered cookies, and logout revocation. Admin tests also cover restricted account creation, rejection of guest/customer access, role separation, service-layer authorization, switching accounts, and idempotent bootstrap that cannot promote a customer. Test containers are removed automatically; tests do not use the application database.

Recovery tests verify hashed tokens, 30-minute expiry, replacement, single use, concurrent reset attempts, password validation, CSRF protection, customer/admin recovery, session and remembered-token revocation, and rollback on delivery failure. Mail tests verify SMTP message construction and transport-failure handling using a mock sender; they do not contact a real email provider.

Profile tests verify customer/admin reads and updates, role and ownership restrictions, validation, CSRF protection, password verification, case-insensitive email changes, duplicate-email rollback, preserved login state for name/phone edits, and revocation of sessions/tokens after email or password changes.

Catalog tests load the original 60-book fixture into an isolated PostgreSQL database and verify stable paging, latest products, structured details, title/description/author search, combined category filters, literal wildcard handling, hidden product/category rules, stock availability, invalid inputs, public access across roles, and demo-import preservation.

Wishlist tests verify empty/saved states, structured product details, idempotent additions/removals, toggling, guest/admin rejection, account isolation, validation, CSRF protection, hidden/zero-stock rules, persistence across logout and email changes, cascading cleanup, service-level authorization, and concurrent add/toggle behavior against PostgreSQL.

Address tests verify empty books, field defaults, default-first ordering, automatic first defaults, explicit/default selection, deletion promotion, ownership and role restrictions, invalid input/CSRF protection, persistence across account changes, cascading deletion, service-level authorization, and concurrent additions/default changes/deletions against PostgreSQL.

Cart tests verify empty totals, matching-format merging, separate formats, default/scalar/absent formats, updates/removals/clearing, exact current-price totals, limits, aggregate stock checks, stale availability, ownership and role protection, input/CSRF validation, persistence, cascading cleanup, and concurrent requests that cannot create duplicate lines or exceed a customer's stock allowance.

The merge regression test starts with an address-branch database at V6 without V5, verifies the startup failure without out-of-order migration, then verifies automatic cart migration, preservation of existing customer/address data, and cart/address operations and error responses in the same customer session.

Payment method tests verify empty lists, normalized masked metadata, type filtering, default ordering/selection, expiry-month boundaries, expired default replacement, removal/promotion and last-active-card protection, ownership and role restrictions, CSRF, raw-card-field rejection, service validation/authorization, database constraints, persistence across account changes, cascading deletion, and concurrent additions/default selections/removals against PostgreSQL.

Checkout/order tests verify nonmutating previews, default/explicit shipping, saved-card selection, current-price totals, format aggregation, stock deduction, owned cart clearing, snapshots surviving source deletion, idempotent retries, ownership/roles/CSRF/CORS, validation and total/status injection rejection, invalid/expired/currency-mismatched selections, database constraints, account-change persistence, simulated storage-failure rollback and retry, concurrent duplicate submissions, and competing-customer stock/deadlock protection against PostgreSQL.

Order history tests verify empty history, bounded paging, timestamp/ID ordering, customer-scoped totals and normalized status filtering, invalid input, guest/admin/service restrictions, attempted owner injection, correctly grouped full item details, historical shipping/product/masked-card snapshots after source deletion, unchanged stock/cart/payment state, persistence after account changes, and consistent count/page contents when an order is committed during a request.

Admin product tests verify hidden product/category access, paging/search/stock filters and summaries, normalized creation, preserved/replaced metadata, SKU conflicts, stock history and rollback, validation/role/CSRF/service/CORS protections, concurrent stock and metadata edits, checkout invalidating stale admin versions, simultaneous checkout/stock correction, deletion clearing cart/wishlist while preserving order history, PNG/JPEG uploads/public delivery, size/dimension/type rejection, and image retention after product deletion.

Admin category tests verify the existing list contract, counts/search/visibility filters, normalized creation and public slug lookup, Unicode/numeric name handling, stable slugs on rename, image clearing, slug conflicts and rollback, live product metadata changes with preserved order snapshots, cart/wishlist/checkout visibility behavior, no-op visibility and safe toggle retries, nonempty deletion protection, validation/role/CSRF/service restrictions, concurrent edits/slug creation, deletion racing product assignment, and hiding racing checkout against PostgreSQL.

Admin order tests verify cross-customer paging/search/status filters, complete historical details and safe contact fields, completion/cancellation audit records, payment-state preservation, terminal/no-op/stale transitions, format-aggregated stock restoration exactly once, inventory version protection, deleted-product handling, stock overflow and storage-failure rollback/retry, legacy cancelled orders, validation/role/CSRF/service restrictions, audit reference cleanup, consistent count/page snapshots, competing updates/cancellations, cancellation racing checkout, and cancellation racing product deletion against PostgreSQL.

Admin customer reporting tests verify empty/zero-order reports, exclusion of admins, safe contact fields, status/payment-aware totals without item duplication, exact decimals and large sums, bounded paging/tie ordering, search/activity/sort filters, filtered summaries, live profile/order/refund updates, checkout/cancellation integration, source/account deletion, validation and endpoint/service authorization, read-only behavior, and consistent count/page/metrics during concurrent registrations and order creation against PostgreSQL.

Admin dashboard tests verify empty stores, lifetime metrics matching customer reporting, paid/cancelled/refunded revenue rules, hidden catalog and stock alerts, bounded recent orders with date/ID ordering and safe fields, UTC day boundaries, zero-filled bounded trends, exact monetary totals without item duplication, live checkout/cancellation/refund/deletion updates, validation and endpoint/service authorization, read-only behavior, generic storage errors/recovery, and one consistent snapshot across all sections during concurrent customer/order/inventory changes against PostgreSQL.

FAQ tests verify public list/detail contracts for guests/customers/admins, empty and hidden content, display-order/ID sorting, live edits/deletion, question/answer and Unicode search, literal wildcard/SQL text, invalid IDs/search lengths, preserved write authorization/CSRF, allowed/rejected CORS origins, database content constraints, existing/hidden content preservation during demo import, concurrent startup imports, and failed import rollback/retry against PostgreSQL.
