# Customer and admin authentication backend

Spring Boot 3.5.16, Spring Security, Spring Session JDBC, and PostgreSQL. This rebuild implements customer authentication, admin authentication/authorization, and password recovery as a JSON API: customer registration, login, logout, 30-day Remember me, separate admin login, restricted admin account creation, and expiring password-reset links.

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
