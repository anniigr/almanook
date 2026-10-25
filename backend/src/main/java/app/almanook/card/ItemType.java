package app.almanook.card;

/** Flat classification of a card. Deliberately not a hierarchy (see docs/data-model.md). */
public enum ItemType {
    PLACE(false),
    MUSEUM(false),
    EXHIBITION(true),
    PERFORMANCE(true),
    CONCERT(true),
    FESTIVAL(true),
    MARKET(true),
    FILM(false),
    RESTAURANT(false),
    ACTIVITY(false),
    EVENT(true),
    OTHER(false);

    private final boolean temporary;

    ItemType(boolean temporary) {
        this.temporary = temporary;
    }

    /** Temporary items get their verified end date as default expiry; permanent ones never get one. */
    public boolean isTemporary() {
        return temporary;
    }
}
