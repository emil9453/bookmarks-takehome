package com.emil.bookmarks.common;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.emil.bookmarks.bookmark.BookmarkNotFoundException;
import com.emil.bookmarks.bookmark.InvalidSortException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.databind.exc.PropertyBindingException;

/**
 * The one place errors turn into responses; no endpoint does any of this itself.
 *
 * <p>Every response is an RFC 9457 problem detail — Spring's own type, and the same shape the
 * framework already uses for the errors it raises, so a client has one error format to parse
 * rather than two.
 *
 * <p>This is a deliberate departure from the house style, which wants a
 * {@code {success, data, error}} envelope around everything. An envelope would mean a generic
 * wrapper type around every model on the Android side; problem details are a published standard
 * that ships in the framework, and only errors carry the extra shape.
 *
 * <p><b>Why this extends {@link ResponseEntityExceptionHandler} rather than standing alone.</b>
 * The base class already turns the framework's own exceptions into the right status — 404 for
 * an unknown path, 405 for the wrong method, 400 for an unparseable path variable, 415, 406.
 * Extending it keeps all of that and overrides only the two responses worth improving. The
 * first attempt here was a bare advice ordered ahead of Spring's, which meant the catch-all
 * below intercepted every one of those and answered 500.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BookmarkNotFoundException.class)
	ProblemDetail handleNotFound(BookmarkNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidSortException.class)
	ProblemDetail handleInvalidSort(InvalidSortException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Failed constraints, reported per field. The inherited version returns a problem detail
	 * whose detail reads "Invalid request content." and says nothing about which field was
	 * wrong — enough to know the request failed, not enough to put a message under the right
	 * input box on the phone.
	 *
	 * <p>The names in {@code errors} are the JSON field names, so a client can look them up
	 * directly against what it sent.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		Map<String, String> errors = ex.getBindingResult()
			.getAllErrors()
			.stream()
			.collect(Collectors.toMap(ApiExceptionHandler::fieldNameOf,
					(error) -> String.valueOf(error.getDefaultMessage()),
					// One field can fail two constraints — too long and blank, say. Keeping
					// both beats picking one arbitrarily.
					(first, second) -> first + "; " + second, TreeMap::new));

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"The request has %d invalid field%s.".formatted(errors.size(), (errors.size() == 1) ? "" : "s"));
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/**
	 * Malformed JSON, or a field the API does not have. The inherited detail is "Failed to read
	 * request", which tells a client nothing it can act on; an unknown field is almost always a
	 * typo, so the response names it.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		String detail = (ex.getCause() instanceof PropertyBindingException cause)
				? "Unknown field '%s'.".formatted(cause.getPropertyName())
				// Deliberately not the parser's own message: it quotes the offending input
				// back, which reflects whatever a caller sent into somebody else's log.
				: "The request body is not valid JSON.";

		return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), headers,
				status, request);
	}

	/**
	 * Anything unforeseen — the database being unreachable is the realistic one. The framework's
	 * own exceptions never reach this, because the base class claims them by a more specific
	 * handler.
	 *
	 * <p>The caller gets a fixed sentence. The cause is logged with the request's trace id, so
	 * it stays recoverable from the logs without a stack trace or an internal class name
	 * crossing the wire.
	 */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
	}

	/**
	 * A failed constraint on a collection element arrives as {@code tags[].<list element>};
	 * trimming it back to {@code tags} gives the client the field it actually sent.
	 */
	private static String fieldNameOf(ObjectError error) {
		if (!(error instanceof FieldError fieldError)) {
			return error.getObjectName();
		}
		String field = fieldError.getField();
		int bracket = field.indexOf('[');
		return (bracket < 0) ? field : field.substring(0, bracket);
	}

}
