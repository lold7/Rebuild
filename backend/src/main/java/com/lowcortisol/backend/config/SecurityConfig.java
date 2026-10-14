package com.lowcortisol.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowcortisol.backend.api.ApiError;
import com.lowcortisol.backend.auth.CustomerService;
import com.lowcortisol.backend.auth.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    public static final String REMEMBER_ME_ATTRIBUTE = "customer.rememberMe";
    public static final int REMEMBER_ME_SECONDS = 30 * 24 * 60 * 60;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    @Primary
    AuthenticationManager authenticationManager(CustomerService customers, PasswordEncoder encoder,
        @Value("${app.security.remember-me-key}") String key) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customers);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(List.of(provider, new RememberMeAuthenticationProvider(key)));
    }

    @Bean
    AuthenticationManager adminAuthenticationManager(AdminService admins, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(admins);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    PersistentTokenBasedRememberMeServices rememberMeServices(
        DataSource dataSource, CustomerService customers,
        @Value("${app.security.remember-me-key}") String key,
        @Value("${app.security.cookie-secure}") boolean secure
    ) {
        if (key.isBlank() || key.length() < 32) {
            throw new IllegalArgumentException("REMEMBER_ME_KEY must contain at least 32 characters");
        }
        JdbcTokenRepositoryImpl tokens = new JdbcTokenRepositoryImpl();
        tokens.setDataSource(dataSource);
        PersistentTokenBasedRememberMeServices services = new PersistentTokenBasedRememberMeServices(key, customers, tokens) {
            @Override
            protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
                return Boolean.TRUE.equals(request.getAttribute(REMEMBER_ME_ATTRIBUTE));
            }

        };
        services.setTokenValiditySeconds(REMEMBER_ME_SECONDS);
        services.setUseSecureCookie(secure);
        services.setCookieName("remember-me");
        return services;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
        @Qualifier("authenticationManager") AuthenticationManager manager,
        HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf,
        PersistentTokenBasedRememberMeServices rememberMe, ObjectMapper mapper,
        @Value("${app.security.remember-me-key}") String key) throws Exception {
        http.authenticationManager(manager)
            .cors(cors -> {})
            .csrf(config -> config.csrfTokenRepository(csrf))
            .securityContext(config -> config.securityContextRepository(contexts))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories", "/api/categories/**", "/api/product-images/*", "/api/faqs", "/api/faqs/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/reset-password/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Storefront shopping features are available to customers and to admins browsing the store.
                .requestMatchers("/api/cart", "/api/cart/**", "/api/checkout", "/api/orders", "/api/orders/**",
                    "/api/account/orders", "/api/account/wishlist", "/api/account/wishlist/**",
                    "/api/account/addresses", "/api/account/addresses/**",
                    "/api/account/payment-methods", "/api/account/payment-methods/**").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers("/api/**").hasRole("CUSTOMER")
                .anyRequest().denyAll())
            .rememberMe(config -> config.key(key).rememberMeServices(rememberMe))
            .logout(config -> config
                .logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout", "POST"))
                .deleteCookies("SESSION")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(config -> config
                .authenticationEntryPoint((request, response, exception) ->
                    writeError(mapper, response, 401, "UNAUTHENTICATED", "Please log in first"))
                .accessDeniedHandler((request, response, exception) ->
                    writeError(mapper, response, 403, "FORBIDDEN", "Access denied or invalid CSRF token")));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.security.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, int status,
        String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ApiError(code, message));
    }
}
