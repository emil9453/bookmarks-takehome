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
	 * Newest first by default, and {@code ?sort=title,asc} overrides it. The page size ceiling
	 * is {@code spring.data.web.pageable.max-page-size}, so {@code ?size=100000} is clamped
	 * rather than served.
	 *
	 * <p>Returns a {@link PagedModel} rather than the {@code Page} itself: the JSON shape of
	 * {@code Page} is the serialised form of an internal Spring Data type and is not a stable
	 * contract, which matters when an Android client is parsing it.
	 */
	@GetMapping
	PagedModel<BookmarkResponse> list(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		// No id in the default sort: the service appends it to every sort, including the ones
		// a client supplies, so naming it here as well would only be a second place to forget.
		return new PagedModel<>(this.service.list(pageable));
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
