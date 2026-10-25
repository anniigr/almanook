package app.almanook.enrichment;

import app.almanook.shared.ApiException;
import org.springframework.http.HttpStatus;

/** The AI provider failed or returned unusable output. Shown to the user; never replaced by fake data. */
public class AiProviderException extends ApiException {

    public AiProviderException(String userMessage) {
        super(HttpStatus.BAD_GATEWAY, "ai_provider_error", userMessage);
    }

    public static AiProviderException unavailable(String message) {
        return new AiProviderException(message);
    }
}
