package app.almanook.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpirationPolicyTest {

    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    static final LocalDate CHRISTMAS_EVE = LocalDate.of(2026, 12, 24);

    static Clock at(ZonedDateTime time) {
        return Clock.fixed(time.toInstant(), ZoneOffset.UTC);
    }

    static boolean expiredAt(LocalDate expiresOn, ZonedDateTime when) {
        return ExpirationPolicy.isExpired(expiresOn, ExpirationPolicy.today(at(when), when.getZone()));
    }

    @Nested
    class LastDayBoundary {

        @Test
        void activeThroughoutTheLastLocalDay() {
            assertThat(expiredAt(CHRISTMAS_EVE, ZonedDateTime.of(2026, 12, 24, 0, 0, 0, 0, WARSAW)))
                    .isFalse();
            assertThat(expiredAt(CHRISTMAS_EVE, ZonedDateTime.of(2026, 12, 24, 23, 59, 59, 999_999_999, WARSAW)))
                    .isFalse();
        }

        @Test
        void expiredFromTheNextLocalMidnight() {
            assertThat(expiredAt(CHRISTMAS_EVE, ZonedDateTime.of(2026, 12, 25, 0, 0, 0, 0, WARSAW)))
                    .isTrue();
        }

        @Test
        void missingExpiryNeverExpires() {
            assertThat(ExpirationPolicy.isExpired(null, LocalDate.of(2999, 1, 1)))
                    .isFalse();
        }
    }

    @Nested
    class Timezones {

        /** 2026-12-24T23:30Z is already Christmas Day in Warsaw but still Christmas Eve in Los Angeles. */
        @Test
        void sameInstantDiffersByUserZone() {
            Clock clock = Clock.fixed(Instant.parse("2026-12-24T23:30:00Z"), ZoneOffset.UTC);
            LocalDate warsawToday = ExpirationPolicy.today(clock, WARSAW);
            LocalDate laToday = ExpirationPolicy.today(clock, ZoneId.of("America/Los_Angeles"));

            assertThat(ExpirationPolicy.isExpired(CHRISTMAS_EVE, warsawToday)).isTrue();
            assertThat(ExpirationPolicy.isExpired(CHRISTMAS_EVE, laToday)).isFalse();
        }

        @ParameterizedTest(name = "{0} at {1} -> local date {2}")
        @CsvSource({
            "Pacific/Kiritimati, 2026-06-30T10:30:00Z, 2026-07-01",
            "Pacific/Pago_Pago,  2026-07-01T10:30:00Z, 2026-06-30",
            "Asia/Kolkata,       2026-06-30T18:29:59Z, 2026-06-30",
            "Asia/Kolkata,       2026-06-30T18:30:00Z, 2026-07-01"
        })
        void extremeAndFractionalOffsets(String zone, String instant, String expectedLocalDate) {
            Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
            assertThat(ExpirationPolicy.today(clock, ZoneId.of(zone))).isEqualTo(LocalDate.parse(expectedLocalDate));
        }
    }

    @Nested
    class DaylightSaving {

        /** 2026-03-29 has 23 hours in Warsaw (02:00 -> 03:00). */
        @Test
        void springForwardDayDoesNotShiftExpiry() {
            LocalDate dstDay = LocalDate.of(2026, 3, 29);
            assertThat(expiredAt(dstDay, ZonedDateTime.of(2026, 3, 29, 23, 59, 0, 0, WARSAW)))
                    .isFalse();
            assertThat(expiredAt(dstDay, ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, WARSAW)))
                    .isTrue();
            // 22:30 UTC on the 29th is 00:30 CEST on the 30th
            Clock clock = Clock.fixed(Instant.parse("2026-03-29T22:30:00Z"), ZoneOffset.UTC);
            assertThat(ExpirationPolicy.today(clock, WARSAW)).isEqualTo(LocalDate.of(2026, 3, 30));
        }

        /** 2026-10-25 has 25 hours in Warsaw (03:00 -> 02:00). */
        @Test
        void fallBackDayDoesNotShiftExpiry() {
            LocalDate dstDay = LocalDate.of(2026, 10, 25);
            // 22:59 UTC on the 25th is 23:59 CET: still the last day
            Clock lastMinute = Clock.fixed(Instant.parse("2026-10-25T22:59:00Z"), ZoneOffset.UTC);
            assertThat(ExpirationPolicy.isExpired(dstDay, ExpirationPolicy.today(lastMinute, WARSAW)))
                    .isFalse();
            Clock midnight = Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneOffset.UTC);
            assertThat(ExpirationPolicy.isExpired(dstDay, ExpirationPolicy.today(midnight, WARSAW)))
                    .isTrue();
        }
    }

    @Nested
    class DefaultExpiry {

        static final LocalDate END = LocalDate.of(2027, 1, 6);

        @Test
        void temporaryEventWithVerifiedEndDateExpiresOnThatDay() {
            assertThat(ExpirationPolicy.defaultExpiry(true, END, FactSource.SOURCE))
                    .contains(END);
            assertThat(ExpirationPolicy.defaultExpiry(true, END, FactSource.USER))
                    .contains(END);
        }

        @Test
        void permanentPlacesNeverGetAnInventedExpiry() {
            assertThat(ExpirationPolicy.defaultExpiry(false, END, FactSource.SOURCE))
                    .isEmpty();
        }

        @Test
        void unverifiedAiEndDateDoesNotBecomeAnExpiry() {
            assertThat(ExpirationPolicy.defaultExpiry(true, END, FactSource.AI)).isEmpty();
        }

        @Test
        void noEndDateMeansNoExpiry() {
            assertThat(ExpirationPolicy.defaultExpiry(true, null, null)).isEmpty();
        }
    }
}
