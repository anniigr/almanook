package app.almanook.sourcing;

import app.almanook.shared.ApiException;
import org.springframework.http.HttpStatus;

/** The URL was allowed but could not be retrieved (timeout, HTTP error, wrong type, too large). */
public class SourceFetchException extends ApiException {

    public SourceFetchException(String reason) {
        super(HttpStatus.BAD_GATEWAY, "source_fetch_failed", "The page could not be retrieved: " + reason);
    }
}
