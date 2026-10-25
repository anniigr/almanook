package app.almanook.search;

/**
 * Season filter semantics: a single season matches cards tagged with it (non-seasonal cards are not added);
 * {@link #ALL} matches everything; {@link #NON_SEASONAL} matches cards without season tags.
 */
public enum SeasonFilter {
    ALL,
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER,
    NON_SEASONAL;

    boolean isSingleSeason() {
        return this != ALL && this != NON_SEASONAL;
    }
}
