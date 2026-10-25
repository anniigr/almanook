package app.almanook.enrichment.provider.anthropic;

import app.almanook.card.ItemType;
import app.almanook.card.Season;
import app.almanook.enrichment.ConfidenceLevel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON schema for {@link app.almanook.enrichment.AiCardProposal}, written out explicitly so that it stays
 * within the structured-output subset (every property required, nullable via type unions, no numeric or
 * length constraints, additionalProperties false).
 */
final class ProposalSchema {

    private ProposalSchema() {}

    static Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("identified", Map.of("type", "boolean"));
        props.put("title", str("Proper name of the place, event, film or experience"));
        props.put("shortDescription", nullableStr("Two or three short practical sentences"));
        props.put("itemType", Map.of("type", "string", "enum", names(ItemType.values())));
        props.put("venueName", nullableStr(null));
        props.put("address", nullableStr("Street address, only if a source states it"));
        props.put("addressSourceUrl", nullableStr(null));
        props.put("city", nullableStr(null));
        props.put("country", nullableStr("Country name in English"));
        props.put("latitude", nullable("number"));
        props.put("longitude", nullable("number"));
        props.put("creator", nullableStr("Director, artist or performer"));
        props.put("releaseYear", nullable("integer"));
        props.put("eventStartDate", nullableDate());
        props.put("eventEndDate", nullableDate());
        props.put("datesSourceUrl", nullableStr(null));
        props.put("priceMin", nullable("number"));
        props.put("priceMax", nullable("number"));
        props.put("currency", nullableStr("ISO 4217 code, e.g. PLN"));
        props.put("priceNote", nullableStr(null));
        props.put("priceSourceUrl", nullableStr(null));
        props.put("openingHoursNote", nullableStr(null));
        props.put("openingHoursSourceUrl", nullableStr(null));
        props.put("officialUrl", nullableStr(null));
        props.put(
                "seasons", Map.of("type", "array", "items", Map.of("type", "string", "enum", names(Season.values()))));
        props.put("imageSearchTerms", nullableStr(null));
        Map<String, Object> level = Map.of("type", "string", "enum", names(ConfidenceLevel.values()));
        props.put(
                "confidence",
                object(new LinkedHashMap<>(
                        Map.of("identification", level, "dates", level, "price", level, "location", level))));
        props.put("unverified", Map.of("type", "array", "items", Map.of("type", "string")));
        return object(props);
    }

    private static Map<String, Object> object(Map<String, Object> props) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("properties", props);
        o.put("required", List.copyOf(props.keySet()));
        o.put("additionalProperties", false);
        return o;
    }

    private static Map<String, Object> str(String description) {
        return description == null ? Map.of("type", "string") : Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> nullableStr(String description) {
        return description == null
                ? Map.of("type", List.of("string", "null"))
                : Map.of("type", List.of("string", "null"), "description", description);
    }

    private static Map<String, Object> nullableDate() {
        return Map.of("type", List.of("string", "null"), "format", "date");
    }

    private static Map<String, Object> nullable(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
