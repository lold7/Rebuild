@echo off
echo [1/3] Ensuring PostgreSQL container is running...
docker compose up -d db

echo [2/3] Setting Environment Variables...
set PORT=3000
set DB_URL=jdbc:postgresql://localhost:5432/bookstore
set DB_USERNAME=bookstore
set DB_PASSWORD=local-bookstore-password
set REMEMBER_ME_KEY=my-super-secret-remember-me-key-32-chars-long
set ALLOWED_ORIGINS=http://localhost:4200,http://localhost:3000
set CATALOG_DEMO_ENABLED=true
rem Sample FAQ entries for the FAQ page (imported only while the FAQ table is empty)
set FAQ_DEMO_ENABLED=true
rem Demo mode shows the reset link on the Forgot Password page instead of sending an email
set PASSWORD_RESET_DELIVERY=demo
set PASSWORD_RESET_LINK_BASE_URL=http://localhost:4200/auth/reset-password
rem Creates the first admin account if it does not exist yet (safe to leave on: an existing admin is never changed)
set BOOTSTRAP_ADMIN_ENABLED=true
set BOOTSTRAP_ADMIN_USERNAME=admin
set BOOTSTRAP_ADMIN_EMAIL=admin@inwza.com
set BOOTSTRAP_ADMIN_PASSWORD=Admin@12345

echo [3/3] Starting Spring Boot Backend on port 3000...
mvnw.cmd spring-boot:run
