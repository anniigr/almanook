package app.almanook.expiration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Pure expiry rules. A card with {@code expiresOn = D} is relevant through the whole local calendar day D in
 * the user's timezone and expired from the next local midnight on. Expiry is never stored: callers evaluate
 * it against {@link #today(Clock, ZoneId)} whenever they read data, so it stays correct after downtime.
 */
public final class ExpirationPolicy {

    private ExpirationPolicy() {}

    /** Today's date in {@code zone}. DST changes cannot shift it because only the local date is used. */
    public static LocalDate today(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    public static boolean isExpired(LocalDate expiresOn, LocalDate today) {
        return expiresOn != null && expiresOn.isBefore(today);
    }

    /**
     * The expiry a card should get when nobody set one explicitly: the verified end date of a temporary
     * event. Permanent things (places, museums, restaurants, films) never receive an invented expiry.
     */
    public static Optional<LocalDate> defaultExpiry(
            boolean temporaryType, LocalDate eventEndDate, FactSource endSource) {
        if (!temporaryType || eventEndDate == null || endSource == null || endSource == FactSource.AI) {
            return Optional.empty();
        }
        return Optional.of(eventEndDate);
    }
}
