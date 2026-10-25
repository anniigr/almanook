package app.almanook.shared;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception into an RFC 9457 problem response with a stable machine-readable
 * {@code code}. Unexpected exceptions are logged and answered with a generic message so internals never
 * leak to clients.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_BASE = "https://almanook.app/problems/";

    public record FieldViolation(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        ProblemDetail problem = problem(ex.status(), ex.code(), ex.getMessage());
        ex.properties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException ex) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), e.getDefaultMessage()))
                .toList();
        List<FieldViolation> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldViolation(e.getObjectName(), e.getDefaultMessage()))
                .toList();
        return validation(concat(errors, global));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleInvalidParameters(HandlerMethodValidationException ex) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldViolation(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validation(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return validation(ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList());
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    ProblemDetail handleMalformed(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "malformed_request", "The request could not be read");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "stale_version", "The card was changed elsewhere; reload it and try again");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.info(
                "Integrity violation answered with 409: {}",
                ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "The change conflicts with existing data");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "The upload exceeds the 5 MiB limit");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Unsupported content type");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "Method not allowed");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not_found", "Resource not found");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong on our side");
    }

    private static ProblemDetail validation(List<FieldViolation> errors) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                errors.size() == 1 ? "1 field is invalid" : errors.size() + " fields are invalid");
        problem.setTitle("Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        return b.isEmpty()
                ? a
                : java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
