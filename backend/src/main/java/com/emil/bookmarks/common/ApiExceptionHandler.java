package com.emil.bookmarks.common;

import com.emil.bookmarks.bookmark.BookmarkNotFoundException;
import com.emil.bookmarks.bookmark.InvalidSortException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place errors turn into responses. Everything here is an RFC 9457 problem detail —
 * Spring's own type, and the same shape the framework already uses for the errors it raises
 * itself, so a client has one error format to parse rather than two.
 */
@RestControllerAdvice
class ApiExceptionHandler {

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
	 * Anything unforeseen — the database being unreachable is the realistic one. Without this
	 * the response falls through to Spring's default error body, so the single error shape the
	 * API promises would break for exactly the failures a client cannot anticipate.
	 *
	 * <p>The caller gets a fixed sentence. The cause is logged with the request's trace id, so
	 * it is recoverable from the logs without a stack trace or an internal class name crossing
	 * the wire.
	 */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
	}

}
