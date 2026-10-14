# Bookstore backend


Spring Boot 3.5.16, Spring Security, Spring Session JDBC, and PostgreSQL. This rebuild provides a public product catalog and search, customer authentication, admin authentication/authorization, profile management, password recovery, customer wishlists, and address books as JSON APIs.

Spring Boot 3.5.16, Spring Security, Spring Session JDBC, and PostgreSQL. This rebuild provides a public product catalog and search, customer authentication, admin authentication/authorization, profile management, password recovery, customer wishlists, and persistent shopping carts as JSON APIs.


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
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Spring Boot does not load `.env` itself. Docker Compose reads it automatically; the local command exports its values explicitly. If using an existing PostgreSQL instance, configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` instead.

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
>>>>>>> feature/cart-management

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

const cart = await apiPost('/api/cart/add', {
  productId: 1, quantity: 2, selectedFormat: 'Paperback'
});
const cartItemId = cart.items[0].cartItemId;
await apiPost('/api/cart/update', { cartItemId, quantity: 3 });
await apiPost('/api/cart/remove', { cartItemId });
await apiPost('/api/cart/clear');
```

PostgreSQL prevents duplicate customer/book/format lines, including products without a format. Account-row locks serialize each customer's writes, and shared product/category locks keep stock and visibility stable during a mutation. Deleting a user or product cascades to its cart entries. No additional environment setting is required; Flyway applies the new schema automatically.
>>>>>>> feature/cart-management

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

async function apiPost(path, body = {}) {
  const csrfResponse = await fetch(`${API}/api/auth/csrf`, {
    credentials: 'include'
  });
  if (!csrfResponse.ok) throw new Error('Unable to obtain CSRF token');
  const csrf = await csrfResponse.json();

  const response = await fetch(`${API}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
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

`V6__address_book.sql` adds `addresses`, with a cascading user foreign key, field limits/defaults, an index for newest addresses per owner, and a partial unique index for one default per customer. Version 6 leaves version 5 available for the separate cart migration. Flyway applies this migration automatically on startup.

`V5__shopping_cart.sql` adds `cart_items`, with user/product foreign keys, cascading deletion, a 1–10 quantity constraint, JSONB selected attributes, a unique customer/product/normalized-format index, and a product index.
>>>>>>> feature/cart-management

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

