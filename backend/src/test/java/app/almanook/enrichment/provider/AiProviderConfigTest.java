package app.almanook.enrichment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiProviderConfigTest {

    final AiProviderConfig config = new AiProviderConfig();
    final JsonMapper json = JsonMapper.builder().build();
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    static AiProperties.Anthropic anthropic(String key) {
        return new AiProperties.Anthropic(
                key, "https://api.anthropic.com", "claude-opus-5-5", "low", 4, true, Duration.ofSeconds(5));
    }

    static AiProperties.Gemini gemini(String key) {
        return new AiProperties.Gemini(
                key, "https://generativelanguage.googleapis.com", "gemini-3.8-flash", "low", Duration.ofSeconds(5));
    }

    String chosen(String provider, String anthropicKey, String geminiKey) {
        return config.aiResearchProvider(
                        new AiProperties(provider, anthropic(anthropicKey), gemini(geminiKey)), json, validator)
                .name();
    }

    @Test
    void autoPicksTheConfiguredProvider() {
        assertThat(chosen("auto", null, "g-key")).isEqualTo("gemini");
        assertThat(chosen("auto", "a-key", null)).isEqualTo("anthropic");
        assertThat(chosen("auto", "a-key", "g-key")).isEqualTo("anthropic");
        assertThat(chosen("auto", "", " ")).isEqualTo("none");
    }

    @Test
    void explicitChoiceWinsAndNeverFallsBackToTheOtherProvider() {
        assertThat(chosen("gemini", "a-key", "g-key")).isEqualTo("gemini");
        assertThat(chosen("Gemini", "a-key", null)).isEqualTo("none");
        assertThat(chosen("none", "a-key", "g-key")).isEqualTo("none");
    }

    @Test
    void unknownProviderFailsFast() {
        assertThatThrownBy(() -> chosen("openai", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto, anthropic, gemini or none");
    }
}
