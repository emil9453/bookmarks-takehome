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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/**
 * {@code /api/v1/bookmarks} is canonical — a breaking change then has an obvious home in
 * {@code /api/v2} rather than forcing every client to move on the same day. {@code /bookmarks}
 * is mapped as well because that is the path the API was specified with, and a caller who uses
 * it should get the resource rather than a 404.
 *
 * <p>Every endpoint is scoped to the caller's {@code X-Client-Id}. That is separation, not
 * authentication — the header is self-asserted and anyone who copies one reads that collection.
 * It exists because the alternative was one global list where every install saw every other
 * install's bookmarks; the brief rules out accounts, and this is the smallest thing that keeps
 * two phones apart without them.
 */
@RestController
@RequestMapping({ "/api/v1/bookmarks", "/bookmarks" })
class BookmarkController {

	/**
	 * What a caller who sends no {@code X-Client-Id} gets. Every install of the app generates its
	 * own id, so this is the bucket for curl, the browser and the OpenAPI page — one shared
	 * collection to poke at, rather than a 400 that makes the API unusable by hand.
	 */
	private static final String SHARED = "shared";

	private static final String CLIENT_ID = "X-Client-Id";

	/** Matches {@code bookmark.client_id}. A longer value would fail in the database, as a 500. */
	private static final int CLIENT_ID_MAX = 64;

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
	PagedModel<BookmarkResponse> list(
			@RequestHeader(name = CLIENT_ID, defaultValue = SHARED) String client,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String tag, @RequestParam(required = false) Boolean favourite,
			@RequestParam(required = false) Boolean favorite,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		// No id in the default sort: the service appends it to every sort, including the ones
		// a client supplies, so naming it here as well would only be a second place to forget.
		return new PagedModel<>(
				this.service.search(clientId(client), q, tag, (favourite != null) ? favourite : favorite, pageable));
	}

	@GetMapping("/{id}")
	BookmarkResponse get(
			@RequestHeader(name = CLIENT_ID, defaultValue = SHARED) String client,
			@PathVariable long id) {
		return this.service.get(clientId(client), id);
	}

	@PostMapping
	ResponseEntity<BookmarkResponse> create(
			@RequestHeader(name = CLIENT_ID, defaultValue = SHARED) String client,
			@Valid @RequestBody CreateRequest request) {
		BookmarkResponse created = this.service.create(clientId(client), request);
		return ResponseEntity.created(URI.create("/api/v1/bookmarks/" + created.id())).body(created);
	}

	/** Partial: send only what changes. This is also the favourite toggle. */
	@PatchMapping("/{id}")
	BookmarkResponse update(
			@RequestHeader(name = CLIENT_ID, defaultValue = SHARED) String client,
			@PathVariable long id, @Valid @RequestBody UpdateRequest request) {
		return this.service.update(clientId(client), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(
			@RequestHeader(name = CLIENT_ID, defaultValue = SHARED) String client,
			@PathVariable long id) {
		this.service.delete(clientId(client), id);
	}

	/**
	 * Checks the header before it can reach an insert as an over-long string and come back a 500.
	 *
	 * <p>Deliberately not {@code @NotBlank @Size} on the parameter itself, which is the obvious
	 * way to write this and is wrong here. A constraint annotation on any parameter switches the
	 * whole method to Spring's built-in method validation, and then a failed {@code @Valid}
	 * {@code @RequestBody} raises {@code HandlerMethodValidationException} instead of
	 * {@code MethodArgumentNotValidException} — so the {@code errors} object keyed by field name
	 * disappears from every validation response, and the add screen loses the message under each
	 * input box. A plain check keeps one validation path for the request body.
	 */
	private static String clientId(String header) {
		if (header.isBlank() || header.length() > CLIENT_ID_MAX) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"%s must be 1 to %d characters.".formatted(CLIENT_ID, CLIENT_ID_MAX));
		}
		return header;
	}

}
