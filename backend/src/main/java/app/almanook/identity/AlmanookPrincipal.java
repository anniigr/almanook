package app.almanook.identity;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as stored in the session. Controllers receive it via
 * {@code @AuthenticationPrincipal}; it is the only source of the current user's id.
 */
public final class AlmanookPrincipal implements UserDetails, Serializable {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final UUID userId;
    private final String email;
    private final transient String passwordHash;

    AlmanookPrincipal(UUID userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static AlmanookPrincipal of(UUID userId, String email) {
        return new AlmanookPrincipal(userId, email, null);
    }

    public UUID userId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Drops the hash once authentication succeeded, so it is never stored in the session. */
    AlmanookPrincipal withoutCredentials() {
        return new AlmanookPrincipal(userId, email, null);
    }
}
