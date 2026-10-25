package app.almanook.expiration;

/**
 * Where a date or price on a card came from. Shown to the user and used to decide whether generated data
 * may replace a value.
 */
public enum FactSource {
    /** Entered or edited by the user. Never overwritten by generated content. */
    USER,
    /** Extracted from a retrieved source page (official site, schema.org data, cited search result). */
    SOURCE,
    /** Suggested by the AI without a verifiable citation. Displayed as unverified. */
    AI
}
