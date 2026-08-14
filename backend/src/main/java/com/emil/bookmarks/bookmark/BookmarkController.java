package com.emil.bookmarks.bookmark;

import java.net.URI;

import com.emil.bookmarks.bookmark.BookmarkDtos.BookmarkResponse;
import com.emil.bookmarks.bookmark.BookmarkDtos.CreateRequest;
import com.emil.bookmarks.bookmark.BookmarkDtos.UpdateRequest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Versioned path, so a breaking change has an obvious home in {@code /api/v2} rather than
 * forcing every client to update on the same day.
 */
@RestController
@RequestMapping("/api/v1/bookmarks")
class BookmarkController {

	private final BookmarkService service;

	BookmarkController(BookmarkService service) {
		this.service = service;
	}

	/**
	 * The one list endpoint. {@code ?q=} searches, {@code ?tag=} and {@code ?favourite=} filter,
	 * and they combine freely — all three, any one, or none, which is the plain list.
	 *
	 * <p>With {@code ?q=} the results come back ranked, best first, and {@code ?sort=} is
	 * demoted to a tiebreak between equally good matches rather than ignored — asking for
	 * relevance and for alphabetical order at the same time has to resolve one way or the
	 * other, and relevance is the point of the search. Without {@code ?q=} it is newest first
	 * by default and {@code ?sort=title,asc} overrides that. The page size ceiling is
	 * {@code spring.data.web.pageable.max-page-size}, which the argument resolver applies — so
	 * this keeps taking a {@code Pageable} parameter rather than building one from a request
	 * field, which would drop the cap with no compile error.
	 *
	 * <p>Returns a {@link PagedModel} rather than the {@code Page} itself: the JSON shape of
	 * {@code Page} is the serialised form of an internal Spring Data type and is not a stable
	 * contract, which matters when an Android client is parsing it.
	 *
	 * <p>{@code favorite} is accepted alongside {@code favourite} because an unknown query
	 * parameter is not an error — Spring drops it and answers 200 with the filter silently
	 * ignored, which is the worst way for a spelling to fail. A request body spelling it the
	 * American way already gets a 400 naming the field; the query string had no such backstop.
	 * British stays canonical, and is what every response and the Android client use.
	 */
	@GetMapping
	PagedModel<BookmarkResponse> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String tag, @RequestParam(required = false) Boolean favourite,
			@RequestParam(required = false) Boolean favorite,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		// No id in the default sort: the service appends it to every sort, including the ones
		// a client supplies, so naming it here as well would only be a second place to forget.
		return new PagedModel<>(
				this.service.search(q, tag, (favourite != null) ? favourite : favorite, pageable));
	}

	@GetMapping("/{id}")
	BookmarkResponse get(@PathVariable long id) {
		return this.service.get(id);
	}

	@PostMapping
	ResponseEntity<BookmarkResponse> create(@Valid @RequestBody CreateRequest request) {
		BookmarkResponse created = this.service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/bookmarks/" + created.id())).body(created);
	}

	/** Partial: send only what changes. This is also the favourite toggle. */
	@PatchMapping("/{id}")
	BookmarkResponse update(@PathVariable long id, @Valid @RequestBody UpdateRequest request) {
		return this.service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		this.service.delete(id);
	}

}
