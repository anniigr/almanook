package app.almanook.search;

import java.time.LocalDate;
import java.util.UUID;

public record SearchCriteria(
        UUID userId,
        Scope scope,
        String query,
        SeasonFilter season,
        SortOrder sort,
        LocalDate today,
        int page,
        int size) {

    public SearchCriteria {
        season = season == null ? SeasonFilter.ALL : season;
        sort = sort == null ? SortOrder.RECENT : sort;
        query = query == null || query.isBlank() ? null : query.strip();
    }
}
