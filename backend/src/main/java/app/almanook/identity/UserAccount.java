package app.almanook.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class UserAccount {

    public static final String DEFAULT_TIMEZONE = "Europe/Warsaw";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Theme theme;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {}

    UserAccount(String email, String passwordHash, String displayName, Instant now) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName.strip();
        this.theme = Theme.SYSTEM;
        this.language = "en";
        this.timezone = DEFAULT_TIMEZONE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    void updatePreferences(Theme theme, String language, ZoneId zone, Instant now) {
        this.theme = theme;
        this.language = language;
        this.timezone = zone.getId();
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Theme getTheme() {
        return theme;
    }

    public String getLanguage() {
        return language;
    }

    public ZoneId getZone() {
        return ZoneId.of(timezone);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
