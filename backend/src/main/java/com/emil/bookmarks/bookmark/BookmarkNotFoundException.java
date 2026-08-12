package com.emil.bookmarks.bookmark;

/**
 * Deliberately carries no HTTP status. The service layer does not know it is behind a web API;
 * mapping this to a 404 is the exception handler's job.
 */
public class BookmarkNotFoundException extends RuntimeException {

	public BookmarkNotFoundException(long id) {
		super("No bookmark with id " + id);
	}

}
