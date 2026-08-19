package com.fantasyiq.api.exception;

import com.fantasyiq.auth.EmailAlreadyInUseException;
import com.fantasyiq.auth.InvalidCredentialsException;
import com.fantasyiq.auth.InvalidRefreshTokenException;
import com.fantasyiq.ingestion.odds.OddsUnavailableException;
import com.fantasyiq.ingestion.stats.EspnUnavailableException;
import com.fantasyiq.domain.player.PlayerNotFoundException;
import com.fantasyiq.ingestion.weather.WeatherUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ProblemDetail handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ProblemDetail handleUnauthorized(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ProblemDetail handlePlayerNotFound(PlayerNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Without this, these exceptions propagate uncaught past DispatcherServlet
     * to the servlet container's default error handling, which forwards to
     * /error -- a path SecurityConfig never explicitly permits, so the client
     * saw a generic 403 instead of a status reflecting what actually happened
     * (discovered manually testing weather ingestion without a real API key
     * yet). Catching here means the response is fully formed within the
     * original request, before any second /error dispatch is needed.
     */
    @ExceptionHandler({EspnUnavailableException.class, WeatherUnavailableException.class, OddsUnavailableException.class})
    public ProblemDetail handleVendorUnavailable(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * Covers @RequestParam/@PathVariable constraint violations (e.g. the
     * @Min/@Max on season/week params, or PlayerController.search's
     * pre-existing @NotBlank on q) -- a distinct exception type from
     * MethodArgumentNotValidException above, which only covers @Valid
     * @RequestBody. @Validated on a @RestController class routes through
     * Spring Boot's MethodValidationPostProcessor (an AOP proxy via
     * MethodValidationInterceptor), which throws jakarta.validation's
     * ConstraintViolationException directly -- not Spring MVC's newer,
     * natively-integrated HandlerMethodValidationException, despite the
     * latter looking like the more obvious fit for @RequestParam. Without
     * this handler these fell through to the catch-all below and surfaced
     * as a misleading 500 instead of a 400 -- true for every @RequestParam
     * constraint in this app, including the pre-existing @NotBlank one,
     * confirmed live via a real stack trace before assuming which
     * exception type was actually being thrown.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParameterValidation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * Catch-all so *any* unhandled exception gets a real 500 instead of
     * silently falling through to the same unpermitted-/error-dispatch 403
     * masking described above -- that masking bit this project twice
     * (WeatherUnavailableException before it got its own handler, then a
     * plain DataIntegrityViolationException from a too-long ingestion_runs.source
     * value) before this existed. Logs the real exception server-side too,
     * since the 403 masking had also been silently swallowing that.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
