# Storefront frontend

Angular 19.2 connects to Spring Boot through /api. The development server proxies these requests to http://localhost:8080, so the browser uses the backend's HttpOnly session cookie on the website's origin. Every write fetches a fresh Spring CSRF token. The frontend does not store bearer tokens or passwords.

## Run locally

Install Node.js 22.x and start Docker Desktop. From the repository root, start the backend:

    cd backend
    docker compose up -d --build

In a second terminal, from the repository root:

    cd frontend
    npm ci
    npm start

Open **http://localhost:5173**. Keep the frontend terminal running. Existing PostgreSQL data stays in the backend's Docker volume. Backend setup and first-admin configuration are documented in [the backend README](../backend/README.md).

The proxy target is configured in proxy.conf.json. The environment uses relative /api and /images URLs. Original covers are served from public/images/products; uploaded covers use /api/product-images/{name}.

The view adapters map backend camelCase DTOs to existing component models. Array-based screens load all backend pages before applying their display controls. Registration is followed by customer login; page reloads restore the customer or admin identity from the server. Logout revokes the backend session. Email/password changes that revoke sessions redirect to login.

Checkout uses backend prices/readiness and shipping addresses. Creation sends an idempotency key that is retained on uncertain failure and replaced after success or changed selections. Payment choices come from the backend; actual saved-card metadata can be selected when available. Orders are created unpaid, and checkout does not charge a payment.

## Checks

    npm run build
    npx playwright install chromium
    npm run test:ci

The production bundle is written to dist/frontend/browser. Production hosting must forward /api to Spring Boot and fall back to index.html for frontend routes. The Angular development proxy is used only by npm start.

## Browser integration tests

Tests use a separate temporary PostgreSQL instance and backend on port 8081. The test database uses memory storage and does not share the normal database or its volume.

From frontend, start a fresh test stack:

    docker compose -p rebuild-frontend-check -f e2e/compose.yml down
    docker compose -p rebuild-frontend-check -f e2e/compose.yml up -d --build

Start a test frontend in another terminal:

    npm start -- --port 5174 --proxy-config e2e/proxy.conf.json

Once both test servers are ready:

    npx playwright install chromium
    npm run test:e2e

Tests verify signup, session restoration, address saving, wishlist, cart, order creation/history, catalog pagination, FAQ retrieval, and admin login/dashboard. They create only temporary test accounts/orders in the isolated database. Afterward, stop the test frontend with Ctrl+C and remove its test backend/database:

    docker compose -p rebuild-frontend-check -f e2e/compose.yml down
