package app.almanook.identity.web;

import app.almanook.identity.AlmanookPrincipal;
import app.almanook.identity.UserAccountService;
import app.almanook.identity.UserAccountService.Me;
import app.almanook.shared.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController {

    record LoginRequest(
            @NotBlank @Email String email, @NotBlank String password) {}

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName) {}

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfAuthenticationStrategy csrfStrategy;
    private final UserAccountService users;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            UserAccountService users) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        this.users = users;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue the XSRF-TOKEN cookie required for state-changing requests")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // forces the deferred token to be generated and written as a cookie
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password; starts a session")
    Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Email or password is incorrect");
        }
        return startSession((AlmanookPrincipal) authentication.getPrincipal(), request, response);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and sign in")
    Me register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        Me me = users.register(body.email(), body.password(), body.displayName());
        return startSession(AlmanookPrincipal.of(me.id(), me.email()), request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out and invalidate the session")
    void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
        csrfStrategy.onAuthentication(null, request, response);
    }

    private Me startSession(AlmanookPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        AlmanookPrincipal sessionPrincipal = AlmanookPrincipal.of(principal.userId(), principal.getUsername());
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                sessionPrincipal, null, sessionPrincipal.getAuthorities());
        if (request.getSession(false) != null) {
            request.changeSessionId(); // session fixation protection
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfStrategy.onAuthentication(authenticated, request, response);
        return users.me(principal.userId());
    }
}
