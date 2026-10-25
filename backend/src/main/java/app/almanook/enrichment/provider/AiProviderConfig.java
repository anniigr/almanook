package app.almanook.enrichment.provider;

import app.almanook.enrichment.AiResearchProvider;
import app.almanook.enrichment.provider.anthropic.AnthropicResearchProvider;
import app.almanook.enrichment.provider.gemini.GeminiResearchProvider;
import jakarta.validation.Validator;
import java.util.Locale;
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
        AiProperties.Gemini gemini = properties.gemini();
        boolean hasAnthropic = anthropic != null && anthropic.configured();
        boolean hasGemini = gemini != null && gemini.configured();
        String choice = properties.provider() == null
                ? "auto"
                : properties.provider().strip().toLowerCase(Locale.ROOT);

        switch (choice) {
            case "anthropic" -> {
                if (hasAnthropic) {
                    return anthropic(anthropic, json, validator);
                }
                log.warn("ALMANOOK_AI_PROVIDER=anthropic but ANTHROPIC_API_KEY is not set; AI enrichment is disabled");
            }
            case "gemini" -> {
                if (hasGemini) {
                    return gemini(gemini, json, validator);
                }
                log.warn("ALMANOOK_AI_PROVIDER=gemini but GEMINI_API_KEY is not set; AI enrichment is disabled");
            }
            case "none" -> log.info("AI enrichment disabled by ALMANOOK_AI_PROVIDER=none");
            case "auto" -> {
                if (hasAnthropic) {
                    return anthropic(anthropic, json, validator);
                }
                if (hasGemini) {
                    return gemini(gemini, json, validator);
                }
                log.info("AI enrichment disabled: neither ANTHROPIC_API_KEY nor GEMINI_API_KEY is set");
            }
            default ->
                throw new IllegalStateException("Unknown almanook.ai.provider '" + properties.provider()
                        + "'; use auto, anthropic, gemini or none");
        }
        return new DisabledAiProvider();
    }

    private static AiResearchProvider anthropic(AiProperties.Anthropic p, JsonMapper json, Validator validator) {
        log.info("AI enrichment enabled: Anthropic {} (effort {})", p.model(), p.effort());
        return new AnthropicResearchProvider(p, json, validator);
    }

    private static AiResearchProvider gemini(AiProperties.Gemini p, JsonMapper json, Validator validator) {
        log.info("AI enrichment enabled: Google Gemini {} (thinking level {})", p.model(), p.thinkingLevel());
        return new GeminiResearchProvider(p, json, validator);
    }
}
