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
 * {@code /api/v1/bookmarks} is canonical — a breaking change then has an obvious home in
 * {@code /api/v2} rather than forcing every client to move on the same day. {@code /bookmarks}
 * is mapped as well because that is the path the API was specified with, and a caller who uses
 * it should get the resource rather than a 404.
 */
@RestController
@RequestMapping({ "/api/v1/bookmarks", "/bookmarks" })
class BookmarkController {

	private final BookmarkService service;

	BookmarkController(BookmarkService service) {
		this.service = service;
	}

	/**
	 * The one list endpoint: {@code ?q=} searches, {@code ?tag=} and {@code ?favourite=} filter,
	 * and they combine freely. With {@code ?q=} results come back ranked and {@code ?sort=} is a
	 * tiebreak between equally good matches; without it, newest first.
	 *
	 * <p>Takes a {@code Pageable} rather than a page-size field so the argument resolver applies
	 * {@code spring.data.web.pageable.max-page-size} — building one by hand drops that cap with
	 * no compile error. Returns {@link PagedModel} rather than {@code Page}, whose JSON is the
	 * serialised form of an internal Spring Data type and not a stable contract to parse against.
	 *
	 * <p>{@code favorite} binds alongside {@code favourite}: an unknown query parameter is not an
	 * error, so the American spelling answered 200 with the filter silently dropped.
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
