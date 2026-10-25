package app.almanook.search;

/** Which of the user's cards a listing covers. */
public enum Scope {
    /** Active collection entries whose card has not expired. */
    COLLECTION,
    /** Pending inbox entries whose card has not expired. */
    DISCOVER_PENDING,
    /** Dismissed inbox entries (any expiry). */
    DISCOVER_DISMISSED,
    /** Active collection entries or pending inbox entries whose card has expired. */
    EXPIRED,
    /** Every card the user owns. */
    ALL_CARDS
}
