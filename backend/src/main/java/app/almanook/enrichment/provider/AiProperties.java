package app.almanook.enrichment.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("almanook.ai")
public record AiProperties(
        /* auto | anthropic | gemini | none. auto picks the first provider that has an API key. */
        @DefaultValue("auto") String provider, Anthropic anthropic, Gemini gemini) {

    public record Anthropic(
            String apiKey,
            @DefaultValue("https://api.anthropic.com") String baseUrl,
            @DefaultValue("claude-opus-5-5") String model,
            /* low | medium | high */
            @DefaultValue("low") String effort,
            @DefaultValue("4") int maxSearches,
            /* Server-side fallback on refusals (Claude API beta "server-side-fallback-2026-07-01"). */
            @DefaultValue("true") boolean refusalFallback,
            @DefaultValue("120s") Duration timeout) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Gemini(
            String apiKey,

            @DefaultValue("https://generativelanguage.googleapis.com")
            String baseUrl,

            @DefaultValue("gemini-3.8-flash") String model,
            /* minimal | low | medium | high */
            @DefaultValue("low") String thinkingLevel,
            @DefaultValue("120s") Duration timeout) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
