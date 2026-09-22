package com.aloute.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Xác thực mỗi request bằng JWT: ưu tiên header {@code Authorization: Bearer} (API/mobile),
 * sau đó tới cookie (trang web). Filter chỉ đọc access token; việc làm mới phiên
 * do {@code /auth/refresh} đảm nhận (xem {@link AuthEntryPoint}).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final List<String> STATIC_PREFIXES =
            List.of("/css/", "/js/", "/img/", "/fonts/", "/webjars/", "/uploads/", "/favicon");

    private final JwtService jwtService;
    private final CookieService cookies;

    public JwtAuthFilter(JwtService jwtService, CookieService cookies) {
        this.jwtService = jwtService;
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        resolveAccessToken(request).flatMap(jwtService::parse).ifPresent(this::authenticate);
        chain.doFilter(request, response);
    }

    private Optional<String> resolveAccessToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return Optional.of(header.substring(BEARER.length()).trim());
        }
        return cookies.read(request, CookieService.ACCESS_COOKIE);
    }

    private void authenticate(AlouteUserPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
