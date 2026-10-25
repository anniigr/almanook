package app.almanook.support;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the full application against a real PostgreSQL (Testcontainers, shared by all ITs). Time is pinned
 * through {@link MutableClock}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AbstractIntegrationTest.TestTimeConfig.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final Instant DEFAULT_NOW = Instant.parse("2026-10-25T10:00:00Z");

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTimeConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(DEFAULT_NOW);
        }
    }

    protected void resetDatabase() {
        jdbc.execute("TRUNCATE card_season, collection_entry, discovery_entry, card, image_upload, app_user CASCADE");
        clock.set(DEFAULT_NOW);
    }
}
