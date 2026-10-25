package app.almanook.enrichment;

import app.almanook.card.CardInput;
import app.almanook.card.ItemType;
import app.almanook.card.Season;
import app.almanook.card.UrlCanonicalizer;
import app.almanook.enrichment.CardDraft.Confidence;
import app.almanook.expiration.ExpirationPolicy;
import app.almanook.expiration.FactSource;
import app.almanook.sourcing.PageSnapshot;
import app.almanook.sourcing.PageSnapshot.StructuredItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Merges user input, the retrieved page and the AI proposal into a draft. The rules (all unit-tested):
 *
 * <ul>
 *   <li>User-entered values (the typed name, seasons, expiry) are never replaced; a differing AI title is
 *       offered as a suggestion instead.
 *   <li>Dates, prices, addresses, coordinates and opening hours count only if they come from the retrieved
 *       page's structured data or cite a URL that was actually retrieved. Otherwise they are dropped and
 *       listed as unverified.
 *   <li>Structured data on the user's page wins over the AI when both exist.
 *   <li>A default expiry is derived only from a verified end date of a temporary item type.
 * </ul>
 */
public final class DraftAssembler {

    private static final Pattern URL_LIKE = Pattern.compile("(?i)^\\s*https?://\\S+\\s*$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    public record Assembled(
            CardInput card,
            Map<String, FactSource> fieldSources,
            Map<String, String> suggestions,
            Confidence confidence,
            List<String> unverifiedFields,
            List<String> missingFields,
            List<SourceRef> sources,
            List<String> warnings,
            String imageSearchTerms,
            List<String> pageImageUrls) {}

    private DraftAssembler() {}

    public static Assembled assemble(DraftRequest req, PageSnapshot page, ResearchNotes notes, AiCardProposal ai) {
        Builder b = new Builder(req, page, notes == null ? ResearchNotes.none() : notes, ai);
        return b.build();
    }

    private static final class Builder {
        final DraftRequest req;
        final PageSnapshot page;
        final ResearchNotes notes;
        final AiCardProposal ai;
        final StructuredItem item;
        final Set<String> evidence = new LinkedHashSet<>();
        final Map<String, FactSource> sources = new LinkedHashMap<>();
        final Map<String, String> suggestions = new LinkedHashMap<>();
        final List<String> unverified = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final Set<String> citedUrls = new LinkedHashSet<>();

        Builder(DraftRequest req, PageSnapshot page, ResearchNotes notes, AiCardProposal ai) {
            this.req = req;
            this.page = page;
            this.notes = notes;
            this.ai = ai;
            this.item = page == null ? null : page.primaryItem().orElse(null);
            if (page != null) {
                canonical(page.url()).ifPresent(evidence::add);
            }
            notes.retrievedUrls().forEach(s -> canonical(s.url()).ifPresent(evidence::add));
        }

        Assembled build() {
            String title = title();
            ItemType type = itemType();
            String description = description();

            String venue =
                    pick("venueName", item == null ? null : item.venueName(), ai == null ? null : ai.venueName(), true);
            String city = pick("city", item == null ? null : item.locality(), ai == null ? null : ai.city(), true);
            String country =
                    pick("country", item == null ? null : item.country(), ai == null ? null : ai.country(), true);
            String address = verifiedText(
                    "address",
                    item == null ? null : item.streetAddress(),
                    ai == null ? null : ai.address(),
                    ai == null ? null : ai.addressSourceUrl());

            BigDecimal lat = null;
            BigDecimal lon = null;
            if (item != null
                    && item.latitude() != null
                    && item.longitude() != null
                    && validCoordinates(item.latitude(), item.longitude())) {
                lat = coordinate(item.latitude());
                lon = coordinate(item.longitude());
                sources.put("coordinates", FactSource.SOURCE);
            } else if (ai != null && ai.latitude() != null && ai.longitude() != null) {
                if (verified(ai.addressSourceUrl()) && validCoordinates(ai.latitude(), ai.longitude())) {
                    lat = coordinate(ai.latitude());
                    lon = coordinate(ai.longitude());
                    sources.put("coordinates", FactSource.SOURCE);
                } else {
                    unverified.add("coordinates");
                }
            }

            LocalDate start = null;
            LocalDate end = null;
            FactSource datesSource = null;
            if (item != null && (item.startDate() != null || item.endDate() != null)) {
                start = item.startDate();
                end = item.endDate();
                datesSource = FactSource.SOURCE;
                LocalDate aiEnd = ai == null ? null : parseDate(ai.eventEndDate());
                if (aiEnd != null && end != null && !aiEnd.equals(end)) {
                    warnings.add("The page and web research disagree on the end date; the page's date (" + end
                            + ") is used.");
                }
            } else if (ai != null && (ai.eventStartDate() != null || ai.eventEndDate() != null)) {
                LocalDate s = parseDate(ai.eventStartDate());
                LocalDate e = parseDate(ai.eventEndDate());
                if (verified(ai.datesSourceUrl()) && (s != null || e != null)) {
                    start = s;
                    end = e;
                    datesSource = FactSource.SOURCE;
                    citedUrls.add(ai.datesSourceUrl());
                } else {
                    unverified.add("eventDates");
                }
            }
            if (start != null && end != null && end.isBefore(start)) {
                warnings.add("The retrieved dates were inconsistent and have been left empty.");
                start = null;
                end = null;
                datesSource = null;
            }
            if (datesSource != null) {
                sources.put("eventDates", datesSource);
            }

            BigDecimal priceMin = null;
            BigDecimal priceMax = null;
            String currency = null;
            String priceNote = null;
            if (item != null && item.priceMin() != null && item.currency() != null) {
                priceMin = money(item.priceMin());
                priceMax = money(item.priceMax());
                currency = item.currency();
                sources.put("price", FactSource.SOURCE);
            } else if (ai != null && ai.priceMin() != null) {
                if (verified(ai.priceSourceUrl())
                        && ai.currency() != null
                        && ai.currency().matches("[A-Z]{3}")
                        && ai.priceMin().signum() >= 0) {
                    priceMin = money(ai.priceMin());
                    priceMax = ai.priceMax() == null || ai.priceMax().compareTo(ai.priceMin()) < 0
                            ? null
                            : money(ai.priceMax());
                    currency = ai.currency();
                    priceNote = truncate(ai.priceNote(), 200);
                    sources.put("price", FactSource.SOURCE);
                    citedUrls.add(ai.priceSourceUrl());
                } else {
                    unverified.add("price");
                }
            }
            if (priceMin != null && priceMax != null && priceMax.compareTo(priceMin) == 0) {
                priceMax = null;
            }

            String hours = null;
            if (ai != null && ai.openingHoursNote() != null) {
                if (verified(ai.openingHoursSourceUrl())) {
                    hours = truncate(ai.openingHoursNote(), 300);
                    sources.put("openingHoursNote", FactSource.SOURCE);
                    citedUrls.add(ai.openingHoursSourceUrl());
                } else {
                    unverified.add("openingHoursNote");
                }
            }

            String sourceUrl = sourceUrl();

            Set<Season> seasons;
            if (req.seasons() != null && !req.seasons().isEmpty()) {
                seasons = EnumSet.copyOf(req.seasons());
                sources.put("seasons", FactSource.USER);
            } else if (ai != null && ai.seasons() != null && !ai.seasons().isEmpty()) {
                seasons = EnumSet.copyOf(ai.seasons());
                sources.put("seasons", FactSource.AI);
            } else {
                seasons = Set.of();
            }

            LocalDate expiresOn = null;
            FactSource expirationSource = null;
            if (req.expiresOn() != null) {
                expiresOn = req.expiresOn();
                expirationSource = FactSource.USER;
            } else {
                Optional<LocalDate> derived = ExpirationPolicy.defaultExpiry(type.isTemporary(), end, datesSource);
                if (derived.isPresent()) {
                    expiresOn = derived.get();
                    expirationSource = datesSource;
                }
            }
            if (expirationSource != null) {
                sources.put("expiresOn", expirationSource);
            }

            String creator = ai == null ? null : truncate(ai.creator(), 200);
            Integer releaseYear =
                    ai == null || ai.releaseYear() == null || ai.releaseYear() < 1870 || ai.releaseYear() > 2200
                            ? null
                            : ai.releaseYear();
            if (creator != null) {
                sources.put("creator", FactSource.AI);
            }

            if (ai != null && ai.unverified() != null) {
                ai.unverified().stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .limit(5)
                        .forEach(s -> warnings.add("Not verified: " + s));
            }

            CardInput card = new CardInput(
                    title,
                    description,
                    type,
                    venue,
                    address,
                    city,
                    country,
                    lat,
                    lon,
                    creator,
                    releaseYear,
                    sourceUrl,
                    priceMin,
                    priceMax,
                    currency,
                    priceNote,
                    priceMin == null ? null : FactSource.SOURCE,
                    start,
                    end,
                    datesSource,
                    expiresOn,
                    expirationSource,
                    hours,
                    seasons,
                    null,
                    null);

            List<String> missing = new ArrayList<>();
            if (priceMin == null) {
                missing.add("price");
            }
            if (type.isTemporary() && start == null && end == null) {
                missing.add("eventDates");
            }
            if (venue == null && city == null && address == null && type != ItemType.FILM) {
                missing.add("location");
            }

            return new Assembled(
                    card,
                    sources,
                    suggestions,
                    confidence(datesSource, priceMin != null, venue != null || city != null || address != null),
                    unverified.stream().distinct().toList(),
                    missing,
                    sourceRefs(sourceUrl),
                    warnings,
                    imageSearchTerms(title, city),
                    page == null ? List.of() : page.imageUrls());
        }

        private String title() {
            String aiTitle = ai == null ? null : clean(ai.title());
            String pageTitle = item != null && item.name() != null ? item.name() : page == null ? null : page.title();
            boolean typedName = req.query() != null
                    && !req.query().isBlank()
                    && !URL_LIKE.matcher(req.query()).matches();
            if (typedName) {
                String typed = truncate(clean(req.query()), 200);
                sources.put("title", FactSource.USER);
                String better = aiTitle != null ? aiTitle : pageTitle;
                if (better != null && !better.equalsIgnoreCase(typed)) {
                    suggestions.put("title", truncate(better, 200));
                }
                return typed;
            }
            if (aiTitle != null) {
                sources.put("title", FactSource.AI);
                return truncate(aiTitle, 200);
            }
            if (pageTitle != null) {
                sources.put("title", FactSource.SOURCE);
                return truncate(pageTitle, 200);
            }
            sources.put("title", FactSource.USER);
            String url = req.url() != null ? req.url() : req.query();
            return truncate(java.net.URI.create(url.strip()).getHost(), 200);
        }

        private String description() {
            String d = null;
            if (ai != null && clean(ai.shortDescription()) != null) {
                d = ai.shortDescription();
                sources.put("description", FactSource.AI);
            } else if (item != null && item.description() != null) {
                d = item.description();
                sources.put("description", FactSource.SOURCE);
            } else if (page != null && page.description() != null) {
                d = page.description();
                sources.put("description", FactSource.SOURCE);
            }
            return d == null ? null : concise(d);
        }

        private ItemType itemType() {
            if (ai != null && ai.itemType() != null) {
                sources.put("itemType", FactSource.AI);
                return ai.itemType();
            }
            ItemType mapped = item == null ? null : mapSchemaType(item.type());
            if (mapped != null) {
                sources.put("itemType", FactSource.SOURCE);
                return mapped;
            }
            return ItemType.OTHER;
        }

        private String sourceUrl() {
            if (req.url() != null && !req.url().isBlank()) {
                return truncate(req.url().strip(), 2048);
            }
            if (req.query() != null && URL_LIKE.matcher(req.query()).matches()) {
                return req.query().strip();
            }
            if (ai != null && verified(ai.officialUrl())) {
                return ai.officialUrl();
            }
            if (notes.officialUrl() != null && verified(notes.officialUrl())) {
                return notes.officialUrl();
            }
            return page == null ? null : page.url();
        }

        /** Identification-style fields: page first (SOURCE), then AI (labelled AI). */
        private String pick(String field, String fromPage, String fromAi, boolean allowAi) {
            if (clean(fromPage) != null) {
                sources.put(field, FactSource.SOURCE);
                return truncate(clean(fromPage), field.equals("country") ? 80 : field.equals("city") ? 120 : 200);
            }
            if (allowAi && clean(fromAi) != null) {
                sources.put(field, FactSource.AI);
                return truncate(clean(fromAi), field.equals("country") ? 80 : field.equals("city") ? 120 : 200);
            }
            return null;
        }

        private String verifiedText(String field, String fromPage, String fromAi, String aiSourceUrl) {
            if (clean(fromPage) != null) {
                sources.put(field, FactSource.SOURCE);
                return truncate(clean(fromPage), 300);
            }
            if (clean(fromAi) != null) {
                if (verified(aiSourceUrl)) {
                    sources.put(field, FactSource.SOURCE);
                    citedUrls.add(aiSourceUrl);
                    return truncate(clean(fromAi), 300);
                }
                unverified.add(field);
            }
            return null;
        }

        private boolean verified(String url) {
            return url != null && canonical(url).map(evidence::contains).orElse(false);
        }

        private Confidence confidence(FactSource dates, boolean hasPrice, boolean hasLocation) {
            if (ai != null && ai.confidence() != null) {
                AiCardProposal.Confidence c = ai.confidence();
                ConfidenceLevel datesLevel = dates == null ? ConfidenceLevel.LOW : c.dates();
                ConfidenceLevel priceLevel = hasPrice ? c.price() : ConfidenceLevel.LOW;
                ConfidenceLevel identification = ai.identified() ? c.identification() : ConfidenceLevel.LOW;
                return new Confidence(
                        ConfidenceLevel.min(identification, c.location()),
                        identification,
                        datesLevel,
                        priceLevel,
                        c.location());
            }
            ConfidenceLevel identification =
                    item != null ? ConfidenceLevel.MEDIUM : page != null ? ConfidenceLevel.LOW : ConfidenceLevel.LOW;
            return new Confidence(
                    identification,
                    identification,
                    dates == FactSource.SOURCE ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW,
                    hasPrice ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW,
                    hasLocation ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW);
        }

        private List<SourceRef> sourceRefs(String sourceUrl) {
            Map<String, SourceRef> refs = new LinkedHashMap<>();
            if (page != null) {
                refs.put(page.url(), new SourceRef(page.url(), page.title()));
            }
            if (sourceUrl != null) {
                refs.putIfAbsent(sourceUrl, new SourceRef(sourceUrl, null));
            }
            for (String cited : citedUrls) {
                refs.putIfAbsent(cited, findTitle(cited));
            }
            for (SourceRef retrieved : notes.retrievedUrls()) {
                if (refs.size() >= 8) {
                    break;
                }
                refs.putIfAbsent(retrieved.url(), retrieved);
            }
            return List.copyOf(refs.values());
        }

        private SourceRef findTitle(String url) {
            return notes.retrievedUrls().stream()
                    .filter(r -> canonical(r.url()).equals(canonical(url)))
                    .findFirst()
                    .orElse(new SourceRef(url, null));
        }

        private String imageSearchTerms(String title, String city) {
            if (ai != null && clean(ai.imageSearchTerms()) != null) {
                return truncate(clean(ai.imageSearchTerms()), 200);
            }
            return truncate(
                    city == null || title.toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))
                            ? title
                            : title + " " + city,
                    200);
        }
    }

    static ItemType mapSchemaType(String schemaType) {
        if (schemaType == null) {
            return null;
        }
        return switch (schemaType.toLowerCase(Locale.ROOT)) {
            case "exhibitionevent", "visualartsevent" -> ItemType.EXHIBITION;
            case "festival" -> ItemType.FESTIVAL;
            case "musicevent" -> ItemType.CONCERT;
            case "theaterevent", "danceevent", "comedyevent" -> ItemType.PERFORMANCE;
            case "saleevent" -> ItemType.MARKET;
            case "event", "socialevent", "childrensevent", "foodevent", "screeningevent" -> ItemType.EVENT;
            case "museum", "artgallery" -> ItemType.MUSEUM;
            case "restaurant", "foodestablishment", "cafeorcoffeeshop", "bar" -> ItemType.RESTAURANT;
            case "movie" -> ItemType.FILM;
            case "place",
                    "touristattraction",
                    "park",
                    "landmarksorhistoricalbuildings",
                    "zoo",
                    "aquarium",
                    "garden",
                    "botanicalgarden",
                    "castle",
                    "church",
                    "civicstructure",
                    "movietheater",
                    "performingartstheater",
                    "musicvenue",
                    "stadiumorarena",
                    "amusementpark" -> ItemType.PLACE;
            default -> null;
        };
    }

    static Optional<String> canonical(String url) {
        return UrlCanonicalizer.canonicalize(url);
    }

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.strip().length() > 10 ? s.strip().substring(0, 10) : s.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** At most three sentences and 600 characters: practical, not promotional. */
    static String concise(String text) {
        String t = clean(text);
        if (t == null) {
            return null;
        }
        String[] sentences = SENTENCE_END.split(t);
        String joined = java.util.Arrays.stream(sentences).limit(3).collect(Collectors.joining(" "));
        return truncate(joined, 600);
    }

    private static boolean validCoordinates(BigDecimal lat, BigDecimal lon) {
        return lat.abs().compareTo(BigDecimal.valueOf(90)) <= 0 && lon.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private static BigDecimal coordinate(BigDecimal v) {
        return v.scale() > 6 ? v.setScale(6, RoundingMode.HALF_UP) : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("\\s+", " ").strip();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 1).strip() + "…";
    }
}
