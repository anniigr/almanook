package app.almanook.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error that maps one-to-one to an RFC 9457 problem response. The message is safe to show to the
 * client; never put internal details (SQL, stack traces, provider payloads) into it.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
