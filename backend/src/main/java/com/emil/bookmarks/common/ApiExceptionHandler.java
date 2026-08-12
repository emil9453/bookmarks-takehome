package com.emil.bookmarks.common;

import com.emil.bookmarks.bookmark.BookmarkNotFoundException;
import com.emil.bookmarks.bookmark.InvalidSortException;

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

	@ExceptionHandler(BookmarkNotFoundException.class)
	ProblemDetail handleNotFound(BookmarkNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidSortException.class)
	ProblemDetail handleInvalidSort(InvalidSortException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

}
