package app.almanook.identity;

import app.almanook.shared.ApiException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService implements UserDetailsService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    UserAccountService(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record Preferences(Theme theme, String language, String timezone) {
        static Preferences of(UserAccount user) {
            return new Preferences(
                    user.getTheme(), user.getLanguage(), user.getZone().getId());
        }
    }

    public record Me(UUID id, String email, String displayName, Preferences preferences) {
        static Me of(UserAccount user) {
            return new Me(user.getId(), user.getEmail(), user.getDisplayName(), Preferences.of(user));
        }
    }

    @Transactional
    public Me register(String email, String rawPassword, String displayName) {
        String normalized = UserAccount.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("email_taken", "An account with this email already exists");
        }
        UserAccount user =
                new UserAccount(normalized, passwordEncoder.encode(rawPassword), displayName, Instant.now(clock));
        return Me.of(users.save(user));
    }

    @Transactional(readOnly = true)
    public Me me(UUID userId) {
        return Me.of(require(userId));
    }

    @Transactional
    public Preferences updatePreferences(UUID userId, Theme theme, String language, String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw ApiException.badRequest(
                    "validation_failed", "Unknown timezone: use an IANA id such as Europe/Warsaw");
        }
        UserAccount user = require(userId);
        user.updatePreferences(theme, language, zone, Instant.now(clock));
        return Preferences.of(user);
    }

    /** The user's preferred timezone; drives every expiry decision. */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID userId) {
        return require(userId).getZone();
    }

    /** The current calendar date in the user's timezone. */
    @Transactional(readOnly = true)
    public LocalDate todayFor(UUID userId) {
        return LocalDate.now(clock.withZone(zoneOf(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public AlmanookPrincipal loadUserByUsername(String username) {
        return users.findByEmail(UserAccount.normalizeEmail(username))
                .map(u -> new AlmanookPrincipal(u.getId(), u.getEmail(), u.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
    }

    @Transactional
    void ensureUser(String email, String rawPassword, String displayName) {
        if (!users.existsByEmail(UserAccount.normalizeEmail(email))) {
            register(email, rawPassword, displayName);
        }
    }

    private UserAccount require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
