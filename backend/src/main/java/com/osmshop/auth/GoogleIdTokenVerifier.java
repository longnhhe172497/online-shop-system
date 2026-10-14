package com.osmshop.auth;

import java.net.URL;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** Verifies Google's signed ID token locally using Google's rotating public keys. */
@Component
public class GoogleIdTokenVerifier {
    private final String clientId;
    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(@Value("${app.google-client-id:}") String clientId) {
        this.clientId = clientId.trim();
        this.decoder = this.clientId.isEmpty() ? null
                : NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
    }

    public boolean enabled() { return decoder != null; }
    public String clientId() { return clientId; }

    public GoogleIdentity verify(String credential) {
        if (decoder == null) throw new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "GOOGLE_NOT_CONFIGURED", "Đăng nhập Google chưa được cấu hình.");
        if (credential == null || credential.length() > 8192 || credential.isBlank())
            throw invalid();
        try {
            Jwt jwt = decoder.decode(credential);
            URL issuer = jwt.getIssuer();
            String iss = issuer == null ? "" : issuer.toString();
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String fullName = jwt.getClaimAsString("name");
            if (!("https://accounts.google.com".equals(iss) || "accounts.google.com".equals(iss))
                    || !jwt.getAudience().contains(clientId)
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                    || subject == null || subject.isBlank() || subject.length() > 255
                    || email == null || email.isBlank() || email.length() > 255) throw invalid();
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (!normalizedEmail.contains("@")) throw invalid();
            String normalizedName = fullName == null || fullName.isBlank() ? normalizedEmail.split("@")[0]
                    : fullName.trim();
            return new GoogleIdentity(subject, normalizedEmail,
                    normalizedName.substring(0, Math.min(150, normalizedName.length())));
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static AuthApiException invalid() {
        return new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_CREDENTIAL",
                "Không xác minh được tài khoản Google. Vui lòng thử lại.");
    }

    public record GoogleIdentity(String subject, String email, String fullName) {}
}
