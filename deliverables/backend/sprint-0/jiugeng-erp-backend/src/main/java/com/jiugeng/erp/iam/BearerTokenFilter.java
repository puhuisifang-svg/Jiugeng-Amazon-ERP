package com.jiugeng.erp.iam;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.jiugeng.erp.iam.IamTypes.SessionPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!mock")
class BearerTokenFilter extends OncePerRequestFilter {
    private static final Set<String> PASSWORD_CHANGE_PATHS = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/logout", "/api/v1/auth/change-password");
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm");
    private final IamService service;

    BearerTokenFilter(IamService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = authorization.substring(7).trim();
        var principal = service.authenticate(token);
        if (principal.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        SessionPrincipal current = principal.get();
        if (current.mustChangePassword() && request.getRequestURI().startsWith("/api/v1/")
                && !PASSWORD_CHANGE_PATHS.contains(request.getRequestURI())) {
            IamSecurityConfiguration.writeProblem(response, 403, "PASSWORD_CHANGE_REQUIRED", "请先修改初始密码");
            return;
        }
        var authorities = current.capabilities().stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(current, token, authorities));
        chain.doFilter(request, response);
    }
}
