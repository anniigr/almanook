package app.almanook.card;

public enum CoverSource {
    /** Image published on the official / source page (og:image, schema.org image). */
    PAGE,
    WIKIMEDIA,
    UNSPLASH,
    /** URL pasted by the user. */
    USER_URL,
    /** File uploaded by the user and stored by Almanook. */
    UPLOAD
}
