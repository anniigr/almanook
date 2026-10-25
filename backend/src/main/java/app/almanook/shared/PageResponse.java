package app.almanook.shared;

import java.util.List;
import java.util.function.Function;

/** Stable JSON shape for paginated listings (independent of Spring Data's {@code Page} serialisation). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) ((totalItems + size - 1) / size);
        return new PageResponse<>(items, page, size, totalItems, totalPages, page + 1 < totalPages);
    }

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, totalItems, totalPages, hasNext);
    }
}
