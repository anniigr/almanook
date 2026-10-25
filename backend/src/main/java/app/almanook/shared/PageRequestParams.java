package app.almanook.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Validated pagination parameters bound from {@code ?page=&size=}. */
public record PageRequestParams(
        @Min(0) Integer page, @Min(1) @Max(100) Integer size) {

    public static final int DEFAULT_SIZE = 24;

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public long offset() {
        return (long) pageOrDefault() * sizeOrDefault();
    }
}
