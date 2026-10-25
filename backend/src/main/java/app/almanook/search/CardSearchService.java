package app.almanook.search;

import app.almanook.card.CardService;
import app.almanook.card.CardView;
import app.almanook.shared.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for every card listing. Filtering, sorting and pagination happen in one SQL statement over the
 * card and entry tables; the page of ids is then hydrated through {@link CardService}. Expiry is evaluated
 * against the caller-supplied local {@code today}, never stored.
 */
@Service
public class CardSearchService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CardService cards;

    CardSearchService(NamedParameterJdbcTemplate jdbc, CardService cards) {
        this.jdbc = jdbc;
        this.cards = cards;
    }

    @Transactional(readOnly = true)
    public PageResponse<CardView> search(SearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", criteria.userId())
                .addValue("today", criteria.today())
                .addValue("limit", criteria.size())
                .addValue("offset", (long) criteria.page() * criteria.size());
        StringBuilder from = new StringBuilder(" FROM card c");
        StringBuilder where = new StringBuilder(" WHERE c.owner_id = :userId");

        switch (criteria.scope()) {
            case COLLECTION -> {
                from.append(" JOIN collection_entry ce ON ce.card_id = c.id AND ce.user_id = :userId"
                        + " AND ce.status = 'ACTIVE'");
                where.append(" AND (c.expires_on IS NULL OR c.expires_on >= :today)");
            }
            case DISCOVER_PENDING -> {
                from.append(" JOIN discovery_entry de ON de.card_id = c.id AND de.user_id = :userId"
                        + " AND de.status = 'PENDING'");
                where.append(" AND (c.expires_on IS NULL OR c.expires_on >= :today)");
            }
            case DISCOVER_DISMISSED ->
                from.append(" JOIN discovery_entry de ON de.card_id = c.id AND de.user_id = :userId"
                        + " AND de.status = 'DISMISSED'");
            case EXPIRED ->
                where.append(" AND c.expires_on < :today AND (EXISTS (SELECT 1 FROM collection_entry ce"
                        + " WHERE ce.card_id = c.id AND ce.user_id = :userId AND ce.status = 'ACTIVE')"
                        + " OR EXISTS (SELECT 1 FROM discovery_entry de WHERE de.card_id = c.id"
                        + " AND de.user_id = :userId AND de.status = 'PENDING'))");
            case ALL_CARDS -> {
                // every owned card
            }
        }

        if (criteria.query() != null) {
            where.append(" AND (lower(c.title) LIKE :q ESCAPE '\\' OR lower(c.description) LIKE :q ESCAPE '\\')");
            params.addValue("q", "%" + escapeLike(criteria.query().toLowerCase(Locale.ROOT)) + "%");
        }
        if (criteria.season().isSingleSeason()) {
            where.append(" AND EXISTS (SELECT 1 FROM card_season s WHERE s.card_id = c.id AND s.season = :season)");
            params.addValue("season", criteria.season().name());
        } else if (criteria.season() == SeasonFilter.NON_SEASONAL) {
            where.append(" AND NOT EXISTS (SELECT 1 FROM card_season s WHERE s.card_id = c.id)");
        }

        long total = jdbc.queryForObject("SELECT count(*)" + from + where, params, Long.class);
        List<UUID> ids = total == 0
                ? List.of()
                : jdbc.queryForList(
                        "SELECT c.id" + from + where + " ORDER BY " + orderBy(criteria)
                                + " LIMIT :limit OFFSET :offset",
                        params,
                        UUID.class);
        return PageResponse.of(
                cards.views(criteria.userId(), ids, criteria.today()), criteria.page(), criteria.size(), total);
    }

    private static String orderBy(SearchCriteria criteria) {
        return switch (criteria.sort()) {
            case ALPHABETICAL -> "lower(c.title) ASC, c.id ASC";
            case EVENT_DATE ->
                "coalesce(c.event_start_date, c.event_end_date) ASC NULLS LAST, lower(c.title) ASC, c.id ASC";
            case RECENT ->
                switch (criteria.scope()) {
                    case COLLECTION -> "ce.saved_at DESC, c.id ASC";
                    case DISCOVER_PENDING, DISCOVER_DISMISSED -> "de.created_at DESC, c.id ASC";
                    case EXPIRED -> "c.expires_on DESC, c.id ASC";
                    case ALL_CARDS -> "c.created_at DESC, c.id ASC";
                };
        };
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
