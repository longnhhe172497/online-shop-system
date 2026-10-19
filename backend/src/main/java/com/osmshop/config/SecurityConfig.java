package com.osmshop.config;

import com.osmshop.auth.BearerAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerAuthenticationFilter bearer)
            throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(SecurityConfig::needsCookieCsrf))
                .cors(cors -> {})
                .headers(headers -> headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, 401, "UNAUTHENTICATED", "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, 403, "FORBIDDEN", "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/verify",
                                "/api/auth/login", "/api/auth/browser-login", "/api/auth/browser-google-login",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm", "/api/auth/verification/resend",
                                "/api/auth/mfa/verify", "/api/auth/browser-mfa/verify",
                                "/api/auth/email-change/confirm", "/api/invitations/accept")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/google/config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()         
                        .requestMatchers("/api/cart", "/api/cart/**").permitAll()                
                        .requestMatchers("/api/me/addresses", "/api/me/addresses/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/manager/orders").hasAuthority("PERM_MANAGER_ORDER_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/manager/orders/*/assign").hasAuthority("PERM_MANAGER_ORDER_ASSIGN")
                        .requestMatchers(HttpMethod.POST, "/api/manager/returns/*/decision").hasAuthority("PERM_MANAGER_RETURN_DECIDE")
                        .requestMatchers(HttpMethod.GET, "/api/support/orders/pending").hasAuthority("PERM_SUPPORT_ORDER_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/support/orders/*/confirm").hasAuthority("PERM_SUPPORT_ORDER_CONFIRM")
                        .requestMatchers(HttpMethod.GET, "/api/warehouse/packing-queue").hasAuthority("PERM_WAREHOUSE_PACK_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/warehouse/orders/*/pack").hasAuthority("PERM_WAREHOUSE_PACK")
                        .requestMatchers(HttpMethod.GET, "/api/warehouse/inventory").hasAuthority("PERM_WAREHOUSE_INVENTORY_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/warehouse/inventory/adjustments").hasAuthority("PERM_WAREHOUSE_INVENTORY_ADJUST")
                        .requestMatchers(HttpMethod.POST, "/api/warehouse/returns/*/inspection").hasAuthority("PERM_WAREHOUSE_RETURN_INSPECT")
                        .requestMatchers(HttpMethod.GET, "/api/delivery/assignments").hasAuthority("PERM_DELIVERY_ASSIGNMENT_VIEW")
                        .requestMatchers(HttpMethod.PATCH, "/api/delivery/attempts/*").hasAuthority("PERM_DELIVERY_ATTEMPT_UPDATE")
                        .requestMatchers("/api/manager/vouchers", "/api/manager/vouchers/*")
                        .hasAuthority("PERM_MANAGER_VOUCHER_MANAGE")
                        .requestMatchers("/api/manager/products", "/api/manager/products/**")    
                        .hasAuthority("PERM_MANAGER_PRODUCT_MANAGE")                             
                        .requestMatchers("/api/manager/categories", "/api/manager/categories/**")
                        .hasAuthority("PERM_MANAGER_CATEGORY_MANAGE")                            
                        .requestMatchers("/api/manager/reviews", "/api/manager/reviews/**")      
                        .hasAuthority("PERM_MANAGER_REVIEW_MODERATE")
                        .requestMatchers("/api/manager/**", "/api/support/**", "/api/warehouse/**", "/api/delivery/**")
                        .denyAll()
                        .requestMatchers("/api/orders", "/api/orders/**", "/api/payment-attempts/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/order-items/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static boolean needsCookieCsrf(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method))
            return false;
        if (request.getCookies() == null) return false;
        for (Cookie cookie : request.getCookies()) {
            if ("FORME_SESSION".equals(cookie.getName()) || "FORME_CART".equals(cookie.getName())) return true;
        }
        return false;
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response,
                                     int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"" + (status == 401 ? "Unauthorized" : "Forbidden")
                + "\",\"status\":" + status + ",\"detail\":\"" + detail
                + "\",\"code\":\"" + code + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors-allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
