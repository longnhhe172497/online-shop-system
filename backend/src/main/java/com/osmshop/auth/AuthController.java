package com.osmshop.auth;

import com.osmshop.auth.AuthDtos.LoginRequest;
import com.osmshop.auth.AuthDtos.LoginResponse;
import com.osmshop.auth.AuthDtos.PasswordResetRequest;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmRequest;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmResponse;
import com.osmshop.auth.AuthDtos.RegisterRequest;
import com.osmshop.auth.AuthDtos.RegisterResponse;
import com.osmshop.auth.AuthDtos.VerifyRequest;
import com.osmshop.auth.AuthDtos.VerifyResponse;
import com.osmshop.auth.AuthDtos.MfaVerifyRequest;
import com.osmshop.auth.AuthDtos.BrowserLoginResponse;
import com.osmshop.cart.CartService;
import com.osmshop.cart.GuestCartCookie;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService auth;
    private final GoogleIdentityService google;
    private final boolean cookieSecure;
    private final CartService carts;
    private final GuestCartCookie cartCookie;

    public AuthController(AuthService auth, GoogleIdentityService google,
                          @Value("${app.cookie-secure:false}") boolean cookieSecure,
                          CartService carts, GuestCartCookie cartCookie) {
        this.auth = auth;
        this.google = google;
        this.cookieSecure = cookieSecure;
        this.carts = carts;
        this.cartCookie = cartCookie;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return auth.register(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        return auth.verify(request.token());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest servletRequest) {
        LoginResponse result = auth.login(request, servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        if (result.mfaRequired()) return ResponseEntity.ok(result);
        return withCookie(result);
    }

    @PostMapping("/browser-login")
    public ResponseEntity<BrowserLoginResponse> browserLogin(@Valid @RequestBody LoginRequest request,
                                                               HttpServletRequest servletRequest) {
        LoginResponse result = auth.login(request, servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        if (result.mfaRequired()) return ResponseEntity.ok(browserResult(result));
        return withBrowserCookie(result, servletRequest);
    }

    @GetMapping("/google/config")
    public Map<String, Object> googleConfig() {
        return Map.of("enabled", google.enabled(), "clientId", google.clientId());
    }

    @PostMapping("/browser-google-login")
    public ResponseEntity<BrowserLoginResponse> browserGoogleLogin(
            @Valid @RequestBody GoogleCredentialRequest request, HttpServletRequest servletRequest) {
        LoginResponse result = google.login(request.credential(), servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        if (result.mfaRequired()) return ResponseEntity.ok(browserResult(result));
        return withBrowserCookie(result, servletRequest);
    }

    public record GoogleCredentialRequest(@jakarta.validation.constraints.NotBlank String credential) {}

    @PostMapping("/mfa/verify")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request,
                                                    HttpServletRequest servletRequest) {
        return withCookie(auth.verifyLoginMfa(request.challengeToken(), request.code(),
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/browser-mfa/verify")
    public ResponseEntity<BrowserLoginResponse> verifyBrowserMfa(@Valid @RequestBody MfaVerifyRequest request,
                                                                   HttpServletRequest servletRequest) {
        return withBrowserCookie(auth.verifyLoginMfa(request.challengeToken(), request.code(),
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")), servletRequest);
    }

    @PostMapping("/verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody PasswordResetRequest request,
                                    HttpServletRequest servletRequest) {
        auth.resendVerification(request.email(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                     HttpServletRequest servletRequest) {
        auth.requestPasswordReset(request.email(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    public PasswordResetConfirmResponse confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        return auth.confirmPasswordReset(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal user,
                                       HttpServletRequest request) {
        auth.logout(user.id(), sessionToken(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                sessionCookie("", Duration.ZERO).toString()).build();
    }

    private ResponseEntity<LoginResponse> withCookie(LoginResponse result) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                sessionCookie(result.accessToken(), Duration.ofHours(24)).toString()).body(result);
    }

    private ResponseEntity<BrowserLoginResponse> withBrowserCookie(LoginResponse result, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                sessionCookie(result.accessToken(), Duration.ofHours(24)).toString());
        String guestToken = cartCookie.read(request);
        if (guestToken != null && result.user() != null) {
            try {
                carts.adoptGuestCart(result.user().id(), result.user().role(), guestToken);
                response.header(HttpHeaders.SET_COOKIE, cartCookie.clear().toString());
            } catch (RuntimeException exception) {
                // Login must still succeed; the guest cart stays and can be merged on the next login.
                log.warn("Guest cart merge failed for user {}", result.user().id(), exception);
            }
        }
        return response.body(browserResult(result));
    }

    private static BrowserLoginResponse browserResult(LoginResponse result) {
        return new BrowserLoginResponse(result.user(), result.mfaRequired(), result.challengeToken(),
                result.mfaMethod(), result.emailDeliveryFailed());
    }

    private ResponseCookie sessionCookie(String value, Duration age) {
        return ResponseCookie.from("FORME_SESSION", value).httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/api").maxAge(age).build();
    }

    public static String sessionToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if ("FORME_SESSION".equals(cookie.getName())) return cookie.getValue();
        }
        throw new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Yêu cầu đăng nhập.");
    }
}
