package app.almanook.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Local single-user convenience: creates a demo account on start-up when
 * {@code almanook.demo-user.enabled=true}. Never enable it on a publicly reachable deployment.
 */
@Component
@ConditionalOnProperty(prefix = "almanook.demo-user", name = "enabled", havingValue = "true")
class DemoUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);

    @ConfigurationProperties("almanook.demo-user")
    record DemoUserProperties(boolean enabled, String email, String password, String displayName) {}

    private final UserAccountService users;
    private final DemoUserProperties properties;

    DemoUserInitializer(UserAccountService users, DemoUserProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.password() == null || properties.password().length() < 10) {
            log.warn("Demo user enabled but ALMANOOK_DEMO_USER_PASSWORD is missing or shorter than 10 characters;"
                    + " skipping");
            return;
        }
        users.ensureUser(properties.email(), properties.password(), properties.displayName());
        log.info("Demo user available: {}", properties.email());
    }
}
