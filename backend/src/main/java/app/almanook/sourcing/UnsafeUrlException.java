package app.almanook.sourcing;

import app.almanook.shared.ApiException;
import org.springframework.http.HttpStatus;

/** The URL (or a redirect target) is not allowed to be fetched by the server. */
public class UnsafeUrlException extends ApiException {

    public UnsafeUrlException(String reason) {
        super(HttpStatus.BAD_REQUEST, "unsafe_url", "This URL cannot be retrieved: " + reason);
    }
}
