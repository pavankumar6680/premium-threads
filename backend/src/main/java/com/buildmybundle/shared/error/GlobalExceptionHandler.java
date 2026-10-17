package com.buildmybundle.shared.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every error into an RFC 9457 {@code application/problem+json} body with a stable {@code code}.
 * Security failures raised inside the filter chain are routed here too (see SecurityConfig), so the API
 * has exactly one error format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	public static final String CODE = "code";

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApi(ApiException ex) {
		ProblemDetail pd = problem(ex.getStatus(), ex.getCode(), ex.getMessage());
		ex.getProperties().forEach(pd::setProperty);
		return pd;
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
				"The resource was modified by another request. Please retry.");
	}

	@ExceptionHandler({ BadCredentialsException.class, DisabledException.class })
	ProblemDetail handleBadCredentials(AuthenticationException ex) {
		// Same message for unknown email, wrong password and disabled account: no account enumeration.
		return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleUnauthenticated(AuthenticationException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "You need to sign in to access this resource.");
	}

	@ExceptionHandler(CsrfException.class)
	ProblemDetail handleCsrf(CsrfException ex) {
		return problem(HttpStatus.FORBIDDEN, "CSRF_INVALID", "Missing or invalid CSRF token.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return problem(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
			.toList();
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.");
		body.setProperty("fieldErrors", violations);
		return ResponseEntity.badRequest().headers(headers).body(body);
	}

	/** Framework-raised errors (bad JSON, type mismatch, 404 route, 405...) also get a {@code code}. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail pd
				&& (pd.getProperties() == null || !pd.getProperties().containsKey(CODE))) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			pd.setProperty(CODE, status != null ? status.name() : "HTTP_" + statusCode.value());
		}
		return response;
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(status.getReasonPhrase());
		pd.setProperty(CODE, code);
		return pd;
	}
}
