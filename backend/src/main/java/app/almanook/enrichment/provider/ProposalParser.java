package app.almanook.enrichment.provider;

import app.almanook.enrichment.AiCardProposal;
import app.almanook.enrichment.AiProviderException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Parses and validates the model's structured output. Shared by every provider adapter. */
public final class ProposalParser {

    private static final Logger log = LoggerFactory.getLogger(ProposalParser.class);

    private final JsonMapper json;
    private final Validator validator;

    public ProposalParser(JsonMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    public AiCardProposal parse(String text) {
        AiCardProposal proposal;
        try {
            proposal = json.readValue(text, AiCardProposal.class);
        } catch (JacksonException e) {
            log.warn("AI returned JSON that does not match the card schema: {}", e.getOriginalMessage());
            throw new AiProviderException(
                    "The AI returned data in an unexpected format. Try again or create the card manually.");
        }
        Set<ConstraintViolation<AiCardProposal>> violations =
                proposal == null ? Set.of() : validator.validate(proposal);
        if (proposal == null || !violations.isEmpty()) {
            log.warn("AI proposal failed validation: {}", violations);
            throw new AiProviderException("The AI returned an incomplete card. Try again or create the card manually.");
        }
        return proposal;
    }
}
