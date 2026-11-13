package com.pawnmaster.api.config;

import com.pawnmaster.api.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/login/**",
                                "/api/v1/auth/**",
                                "/api/v1/public/**",
                                "/api/v1/branches/provinces",
                                "/api/v1/branches/provinces/**",
                                "/api/v1/branches/districts",
                                "/api/v1/branches/districts/**",
                                "/api/v1/branches/wards",
                                "/api/v1/branches/wards/**",
                                "/api/v1/collaterals/evaluate-ltv",
                                "/api/v1/collaterals/categories",
                                "/api/v1/banks",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/customers/occupation-groups",
                                "/api/v1/customers/verification-doc-types",
                                "/api/v1/customers/verification-doc-policies"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    String uri = request.getRequestURI();
                    String accept = request.getHeader("Accept");
                    boolean isApiRequest = uri != null && uri.startsWith("/api/");
                    boolean isBrowserView = !isApiRequest && (
                            "/".equals(uri) ||
                            (uri != null && (uri.startsWith("/dashboard") || uri.startsWith("/contracts"))) ||
                            (accept != null && accept.contains("text/html"))
                    );
                    if (isBrowserView) {
                        response.sendRedirect("/login");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"success\":false,\"code\":401,\"message\":\"Vui lòng đăng nhập để truy cập hệ thống\"}");
                    }
                }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}


