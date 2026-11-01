package com.adria.atlasdesk.security;

import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;

@Component
public class OrganizationAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttempts;

    public OrganizationAuthenticationProvider(UserRepository users, PasswordEncoder passwordEncoder,
            LoginAttemptLimiter loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OrganizationLoginAuthenticationToken login = (OrganizationLoginAuthenticationToken) authentication;
        loginAttempts.recordAttempt(login.getOrganizationId(), login.getEmail());
        User user = users.findByOrganizationIdAndEmailIgnoreCase(login.getOrganizationId(), login.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches((String) login.getCredentials(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new DisabledException("Account is disabled");
        }
        loginAttempts.clear(login.getOrganizationId(), login.getEmail());

        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getOrganization().getId(),
                user.getName(), user.getEmail(), user.getRole());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OrganizationLoginAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
