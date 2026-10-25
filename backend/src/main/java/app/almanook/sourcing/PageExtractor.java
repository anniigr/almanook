package app.almanook.sourcing;

import app.almanook.sourcing.PageSnapshot.StructuredItem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Extracts title, Open Graph metadata, schema.org JSON-LD entities and readable text from HTML. */
@Component
public class PageExtractor {

    static final int MAX_TEXT_CHARS = 12_000;
    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Set<String> INTERESTING_TYPES = Set.of(
            "event",
            "exhibitionevent",
            "festival",
            "musicevent",
            "theaterevent",
            "screeningevent",
            "socialevent",
            "childrensevent",
            "comedyevent",
            "danceevent",
            "foodevent",
            "visualartsevent",
            "saleevent",
            "place",
            "touristattraction",
            "museum",
            "park",
            "landmarksorhistoricalbuildings",
            "restaurant",
            "foodestablishment",
            "cafeorcoffeeshop",
            "bar",
            "movietheater",
            "performingartstheater",
            "musicvenue",
            "zoo",
            "aquarium",
            "garden",
            "civicstructure",
            "localbusiness",
            "movie",
            "creativework",
            "church",
            "castle",
            "botanicalgarden",
            "artgallery",
            "stadiumorarena",
            "amusementpark");

    private final JsonMapper json = JsonMapper.builder().build();

    public PageSnapshot extract(FetchedResource resource) {
        Document doc;
        try {
            doc = Jsoup.parse(
                    new ByteArrayInputStream(resource.body()),
                    resource.charset() == null ? null : resource.charset().name(),
                    resource.finalUri().toString());
        } catch (IOException e) {
            throw new SourceFetchException("the page could not be parsed");
        }
        return extract(doc, resource.finalUri().toString());
    }

    PageSnapshot extract(Document doc, String url) {
        List<StructuredItem> items = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                collect(json.readTree(script.data()), items, 0);
            } catch (JacksonException e) {
                // malformed JSON-LD is common; ignore that block
            }
        }

        Set<String> images = new LinkedHashSet<>();
        meta(doc, "og:image:secure_url").ifPresent(images::add);
        meta(doc, "og:image").ifPresent(images::add);
        meta(doc, "twitter:image").ifPresent(images::add);
        items.stream().map(StructuredItem::imageUrl).filter(u -> u != null).forEach(images::add);
        List<String> absoluteImages = images.stream()
                .map(u -> absolutize(doc, u))
                .filter(u -> u != null && u.toLowerCase(Locale.ROOT).startsWith("https://"))
                .limit(6)
                .toList();

        String title = meta(doc, "og:title").orElse(clean(doc.title()));
        String description =
                meta(doc, "og:description").or(() -> meta(doc, "description")).orElse(null);
        return new PageSnapshot(
                url,
                truncate(title, 300),
                truncate(description, 1000),
                meta(doc, "og:site_name").orElse(null),
                clean(doc.select("html").attr("lang")),
                absoluteImages,
                List.copyOf(items),
                readableText(doc));
    }

    private void collect(JsonNode node, List<StructuredItem> out, int depth) {
        if (node == null || depth > 6 || out.size() >= 20) {
            return;
        }
        if (node.isArray()) {
            node.forEach(n -> collect(n, out, depth + 1));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has("@graph")) {
            collect(node.get("@graph"), out, depth + 1);
        }
        String type = firstText(node.get("@type"));
        if (type != null && INTERESTING_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            out.add(toItem(node, type));
        }
        // events are often nested in a place ("event": [...]) or an ItemList ("itemListElement")
        for (String nested : List.of("event", "events", "subEvent", "itemListElement", "item", "mainEntity")) {
            if (node.has(nested)) {
                collect(node.get(nested), out, depth + 1);
            }
        }
    }

    private StructuredItem toItem(JsonNode node, String type) {
        JsonNode location = first(node.get("location"));
        JsonNode address = location != null ? first(location.get("address")) : first(node.get("address"));
        JsonNode geo = location != null && location.has("geo") ? first(location.get("geo")) : first(node.get("geo"));
        JsonNode offers = node.get("offers");
        BigDecimal[] prices = priceRange(offers);
        String currency = offers == null ? null : firstText(findField(offers, "priceCurrency"));
        return new StructuredItem(
                type,
                truncate(text(node.get("name")), 200),
                truncate(text(node.get("description")), 1200),
                date(node.get("startDate")),
                date(node.get("endDate")),
                location == null ? null : truncate(text(location.get("name")), 200),
                address == null
                        ? null
                        : truncate(address.isString() ? address.asString() : text(address.get("streetAddress")), 300),
                address == null || address.isString() ? null : truncate(text(address.get("addressLocality")), 120),
                address == null || address.isString() ? null : truncate(countryName(address.get("addressCountry")), 80),
                geo == null ? null : decimal(geo.get("latitude")),
                geo == null ? null : decimal(geo.get("longitude")),
                prices[0],
                prices[1],
                currency != null && currency.matches("[A-Z]{3}") ? currency : null,
                imageUrl(node.get("image")),
                text(node.get("url")));
    }

    private BigDecimal[] priceRange(JsonNode offers) {
        if (offers == null) {
            return new BigDecimal[] {null, null};
        }
        List<BigDecimal> values = new ArrayList<>();
        for (JsonNode offer : offers.isArray() ? offers : List.of(offers)) {
            for (String field : List.of("price", "lowPrice", "highPrice")) {
                BigDecimal v = decimal(offer.get(field));
                if (v != null) {
                    values.add(v);
                }
            }
        }
        if (values.isEmpty()) {
            return new BigDecimal[] {null, null};
        }
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElseThrow();
        return new BigDecimal[] {min, max};
    }

    private static JsonNode findField(JsonNode node, String field) {
        if (node.isArray()) {
            for (JsonNode n : node) {
                if (n.has(field)) {
                    return n.get(field);
                }
            }
            return null;
        }
        return node.get(field);
    }

    private static String countryName(JsonNode country) {
        if (country == null) {
            return null;
        }
        if (country.isObject()) {
            return text(country.get("name"));
        }
        String c = text(country);
        if (c != null && c.length() == 2) {
            String name = Locale.of("", c.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
            return name.isBlank() ? c : name;
        }
        return c;
    }

    private static String imageUrl(JsonNode image) {
        JsonNode first = first(image);
        if (first == null) {
            return null;
        }
        return first.isObject() ? text(first.get("url")) : text(first);
    }

    private static LocalDate date(JsonNode node) {
        String s = text(node);
        if (s == null) {
            return null;
        }
        Matcher m = ISO_DATE.matcher(s);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.parse(m.group(1));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        Matcher m = NUMBER.matcher(node.asString());
        if (!m.find()) {
            return null;
        }
        try {
            return new BigDecimal(m.group().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonNode first(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isArray() ? (node.isEmpty() ? null : node.get(0)) : node;
    }

    private static String firstText(JsonNode node) {
        return text(first(node));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isContainer()) {
            return null;
        }
        return clean(Jsoup.parse(node.asString()).text());
    }

    private static java.util.Optional<String> meta(Document doc, String key) {
        Element e = doc.selectFirst("meta[property=" + key + "], meta[name=" + key + "]");
        return java.util.Optional.ofNullable(e).map(el -> clean(el.attr("content")));
    }

    private static String absolutize(Document doc, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return java.net.URI.create(doc.location()).resolve(url.strip()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String readableText(Document doc) {
        Document copy = doc.clone();
        copy.select("script, style, noscript, svg, nav, footer, header, form, iframe, template, [aria-hidden=true]")
                .remove();
        Element main = copy.selectFirst("main, article, [role=main]");
        String text = clean((main != null ? main : copy.body() != null ? copy.body() : copy).text());
        return truncate(text, MAX_TEXT_CHARS);
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
