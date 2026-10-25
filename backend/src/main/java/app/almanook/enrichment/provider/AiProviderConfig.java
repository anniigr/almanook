package app.almanook.enrichment.provider;

import app.almanook.enrichment.AiResearchProvider;
import app.almanook.enrichment.provider.anthropic.AnthropicResearchProvider;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    AiResearchProvider aiResearchProvider(AiProperties properties, JsonMapper json, Validator validator) {
        AiProperties.Anthropic anthropic = properties.anthropic();
        if (anthropic != null && anthropic.configured()) {
            log.info("AI enrichment enabled: Anthropic {} (effort {})", anthropic.model(), anthropic.effort());
            return new AnthropicResearchProvider(anthropic, json, validator);
        }
        log.info("AI enrichment disabled: ANTHROPIC_API_KEY is not set");
        return new DisabledAiProvider();
    }
}
