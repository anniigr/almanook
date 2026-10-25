package app.almanook.enrichment;

import app.almanook.card.Season;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/** What the user typed in the Add Card dialog. User-provided seasons and expiry always win over AI output. */
public record DraftRequest(
        @Size(max = 300) String query,

        @Size(max = 2048) @Pattern(regexp = "(?i)https?://\\S+", message = "must be an http(s) URL")
        String url,

        @Size(max = 2000) String notes,
        @Size(max = 4) Set<Season> seasons,
        LocalDate expiresOn) {

    @JsonIgnore
    @AssertTrue(message = "enter a name, a URL or both")
    public boolean isQueryOrUrlPresent() {
        return (query != null && !query.isBlank()) || (url != null && !url.isBlank());
    }

    /** The user's input, preserved verbatim on the card. */
    public String originalInput() {
        StringBuilder sb = new StringBuilder();
        if (query != null && !query.isBlank()) {
            sb.append(query.strip());
        }
        if (url != null && !url.isBlank()) {
            sb.append(sb.isEmpty() ? "" : "\n").append(url.strip());
        }
        if (notes != null && !notes.isBlank()) {
            sb.append(sb.isEmpty() ? "" : "\n").append("Notes: ").append(notes.strip());
        }
        return sb.toString();
    }
}
