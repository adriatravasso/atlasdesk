package com.adria.atlasdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final CurrentUserContext currentUser;

    public AuthController(AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
            CurrentUserContext currentUser) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new OrganizationLoginAuthenticationToken(login.organizationId(), login.email(), login.password()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("invalid_credentials"));
        }

        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new AuthResponse(toUserResponse((AuthenticatedUser) authentication.getPrincipal())));
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return toUserResponse(currentUser.requireUser());
    }

    private static AuthUserResponse toUserResponse(AuthenticatedUser user) {
        return new AuthUserResponse(user.id(), user.organizationId(), user.name(), user.email(), user.role().name());
    }

    public record LoginRequest(@NotNull Long organizationId, @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record AuthResponse(AuthUserResponse user) {
    }

    public record AuthUserResponse(Long id, Long organizationId, String name, String email, String role) {
    }

    public record ApiError(String error) {
    }
}
