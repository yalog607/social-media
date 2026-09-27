package com.aloute.config;

import com.aloute.security.AlouteAccessDeniedHandler;
import com.aloute.security.AuthEntryPoint;
import com.aloute.security.CookieService;
import com.aloute.security.CsrfCookieFilter;
import com.aloute.security.JwtAuthFilter;
import com.aloute.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Clock;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_STATIC = {
            "/css/**", "/js/**", "/img/**", "/fonts/**", "/webjars/**", "/favicon.ico", "/uploads/**", "/error"
    };
    private static final String[] PUBLIC_PAGES = {
            "/", "/login", "/register", "/forgot-password", "/reset-password", "/u/**", "/dev/**"
    };

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** USER ⊂ CREATOR, USER ⊂ MANAGER ⊂ ADMIN. Manager không tự động có quyền Creator. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_MANAGER
                ROLE_MANAGER > ROLE_USER
                ROLE_CREATOR > ROLE_USER
                """);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, CookieService cookies)
            throws Exception {
        // Handler thường (không XOR): JS đọc giá trị thô của cookie XSRF-TOKEN rồi gửi lại qua header X-XSRF-TOKEN.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.deny()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new AuthEntryPoint())
                        .accessDeniedHandler(new AlouteAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_STATIC).permitAll()
                        .requestMatchers(PUBLIC_PAGES).permitAll()
                        .requestMatchers("/auth/**", "/logout").permitAll()
                        // Link chia sẻ một bài: khách xem được nếu bài công khai (PostService quyết định, còn lại là 404)
                        .requestMatchers(HttpMethod.GET, "/posts/*").permitAll()
                        // Danh sách bình luận đi kèm trang chi tiết bài nên cùng quy tắc xem: khách thấy nếu bài công khai
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/comments").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/manage/**").hasRole("MANAGER")
                        .requestMatchers("/creator/**").hasRole("CREATOR")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService, cookies), BasicAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }
}
