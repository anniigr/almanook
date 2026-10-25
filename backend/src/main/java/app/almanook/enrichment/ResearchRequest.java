package app.almanook.enrichment;

import app.almanook.sourcing.PageSnapshot;
import java.time.LocalDate;

/** Everything the provider may use. {@code page} is untrusted third-party content. */
public record ResearchRequest(String query, String url, String notes, LocalDate today, PageSnapshot page) {}
