package app.almanook.enrichment;

public enum ConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH;

    static ConfidenceLevel min(ConfidenceLevel... levels) {
        ConfidenceLevel result = HIGH;
        for (ConfidenceLevel l : levels) {
            if (l != null && l.ordinal() < result.ordinal()) {
                result = l;
            }
        }
        return result;
    }
}
