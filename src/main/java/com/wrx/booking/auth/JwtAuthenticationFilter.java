package com.wrx.booking.auth;

import com.wrx.booking.support.AuthenticatedUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("!perf")
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/logout")) {
            chain.doFilter(request, response); return;
        }
        if (!path.startsWith("/api/")) { chain.doFilter(request, response); return; }
        String token = token(request);
        if (token == null) { reject(response, 401, "未登录"); return; }
        try {
            AuthenticatedUser user = jwtService.parse(token);
            request.setAttribute(AuthenticatedUserContext.REQUEST_USER_ATTRIBUTE, user);
            if (requiresAdmin(path) && !user.isAdmin()) { reject(response, 403, "没有管理员权限"); return; }
            chain.doFilter(request, response);
        } catch (IllegalArgumentException e) { reject(response, 401, "登录已失效"); }
    }

    private boolean requiresAdmin(String path) { return path.startsWith("/api/dev") || path.startsWith("/api/dead-letters") || path.startsWith("/api/admin"); }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if ("AUTH_TOKEN".equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
