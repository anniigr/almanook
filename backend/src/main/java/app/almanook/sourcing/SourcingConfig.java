package app.almanook.sourcing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SourcingConfig {

    @Bean
    UrlSafetyPolicy urlSafetyPolicy() {
        return new UrlSafetyPolicy();
    }

    @Bean(destroyMethod = "close")
    SafeHttpFetcher safeHttpFetcher(UrlSafetyPolicy policy) {
        return new SafeHttpFetcher(policy);
    }
}
