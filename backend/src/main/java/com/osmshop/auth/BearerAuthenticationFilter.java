package com.osmshop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public BearerAuthenticationFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            token = header.substring(7).trim();
        } else if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("FORME_SESSION".equals(cookie.getName())) { token = cookie.getValue(); break; }
            }
        }
        if (token != null && !token.isBlank()) {
            String tokenHash = AuthService.hash(token);
            jdbc.update("""
                    UPDATE user_sessions SET revoked_at=now()
                    WHERE token_hash=? AND revoked_at IS NULL
                      AND coalesce(last_seen_at,created_at)<=now()-interval '30 minutes'
                    """, tokenHash);
            List<AuthenticatedSession> sessions = jdbc.query("""
                    SELECT s.id,u.id,u.email,u.full_name,u.role
                    FROM user_sessions s JOIN users u ON u.id = s.user_id
                    WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > now()
                      AND coalesce(s.last_seen_at,s.created_at)>now()-interval '30 minutes'
                      AND u.status = 'ACTIVE' AND u.verified_at IS NOT NULL
                    """, (rs, row) -> new AuthenticatedSession(rs.getLong(1),
                            new AuthPrincipal(rs.getLong(2), rs.getString(3), rs.getString(4),
                                    rs.getString(5))), tokenHash);
            if (!sessions.isEmpty()) {
                AuthenticatedSession session = sessions.getFirst();
                AuthPrincipal user = session.user();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
                jdbc.update("UPDATE user_sessions SET last_seen_at=now() WHERE id=?", session.id());
            }
        }
        chain.doFilter(request, response);
    }

    private record AuthenticatedSession(long id, AuthPrincipal user) {}
}
