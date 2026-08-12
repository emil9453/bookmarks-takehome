package com.emil.bookmarks.bookmark;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the API speaks. Entities never leave the service layer — {@code open-in-view} is off, so
 * a lazily loaded tag collection reaching the serialiser would throw rather than quietly issue
 * another query, and the database schema stays free to change without breaking clients.
 *
 * <p>The three records live in one file because they are one contract, read together.
 */
final class BookmarkDtos {

	private BookmarkDtos() {
	}

	/**
	 * At least one non-whitespace character. {@code @NotBlank} cannot be used on the update
	 * request: it rejects null, and null there means "leave this field alone". A pattern skips
	 * nulls, so it rejects {@code ""} and {@code "   "} without breaking partial updates.
	 */
	private static final String NOT_BLANK = ".*\\S.*";

	/**
	 * {@code @URL} on its own accepts any scheme {@code java.net.URL} knows, so
	 * {@code file:///etc/passwd} and {@code ftp://…} would both pass. A read-it-later app saves
	 * web pages; anything else is a mistake worth telling the caller about. The extra pattern
	 * narrows it to http and https without needing a second annotation.
	 */
	private static final String HTTP_ONLY = "^https?://.+";

	private static final String WEB_LINK = "must be a valid http or https link";

	/**
	 * Lengths match the column widths in V1 exactly; drifting apart turns a 400 into a 500.
	 *
	 * <p>{@code favourite} is a boxed {@code Boolean} because it is optional. Jackson 3 refuses
	 * to map an absent field onto a primitive rather than defaulting it — as a {@code boolean}
	 * every request that left the field out was rejected.
	 */
	record CreateRequest(@NotBlank @URL(regexp = HTTP_ONLY, message = WEB_LINK) @Size(max = 2048) String url,
			@NotBlank @Size(max = 200) String title, Set<@NotBlank @Size(max = 50) String> tags,
			@Size(max = 2000) String notes, Boolean favourite) {
	}

	/**
	 * Every field is optional: null means "leave this alone", which is what makes flipping the
	 * favourite flag a one-field request rather than a full round trip of the whole bookmark.
	 *
	 * <p>The cost of that convention is that null cannot also mean "clear this" — send an empty
	 * string to blank the notes. Distinguishing the two would mean wrapping every field.
	 */
	record UpdateRequest(@URL(regexp = HTTP_ONLY, message = WEB_LINK) @Size(max = 2048) String url,
			@Pattern(regexp = NOT_BLANK, message = "must not be blank") @Size(max = 200) String title,
			Set<@NotBlank @Size(max = 50) String> tags, @Size(max = 2000) String notes, Boolean favourite) {
	}

	record BookmarkResponse(Long id, String url, String title, Set<String> tags, String notes, boolean favourite,
			Instant createdAt, Instant updatedAt) {

		static BookmarkResponse of(Bookmark bookmark) {
			return new BookmarkResponse(bookmark.getId(), bookmark.getUrl(), bookmark.getTitle(),
					// Sorted, so the same bookmark always serialises the same way.
					new TreeSet<>(bookmark.getTags()), bookmark.getNotes(), bookmark.isFavourite(),
					bookmark.getCreatedAt(), bookmark.getUpdatedAt());
		}
	}

}
