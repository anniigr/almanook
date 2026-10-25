package app.almanook.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small builder for card JSON bodies in tests. */
final class Cards {

    private final Map<String, Object> card = new HashMap<>();

    private Cards(String title, String type) {
        card.put("title", title);
        card.put("itemType", type);
    }

    static Cards card(String title, String type) {
        return new Cards(title, type);
    }

    Cards with(String field, Object value) {
        card.put(field, value);
        return this;
    }

    Cards seasons(String... seasons) {
        card.put("seasons", List.of(seasons));
        return this;
    }

    Map<String, Object> fields() {
        return card;
    }

    String create(String destination) {
        return app.almanook.support.ApiClient.json(Map.of("card", card, "destination", destination));
    }
}
