package com.emil.bookmarks.bookmark;

import java.util.Locale;
import java.util.Set;

import com.emil.bookmarks.bookmark.BookmarkDtos.BookmarkResponse;
import com.emil.bookmarks.bookmark.BookmarkDtos.CreateRequest;
import com.emil.bookmarks.bookmark.BookmarkDtos.UpdateRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class BookmarkService {

	private final BookmarkRepository repository;

	BookmarkService(BookmarkRepository repository) {
		this.repository = repository;
	}

	/**
	 * Sort keys the API offers. An allow-list rather than "whatever the entity has": entity
	 * field names are not a contract, and an unknown one otherwise reaches Spring Data and
	 * surfaces as a 500 for what is a client typo.
	 */
	private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "title", "favourite", "id");

	/**
	 * Maps to DTOs inside the transaction on purpose. The tag collection is lazy and
	 * {@code open-in-view} is off, so returning entities and mapping in the controller would
	 * fail on the first bookmark that has tags.
	 */
	@Transactional(readOnly = true)
	Page<BookmarkResponse> search(String query, String tag, Boolean favourite, Pageable pageable) {
		return this.repository.search(likePattern(query), normalise(tag), favourite, totalOrder(pageable))
			.map(BookmarkResponse::of);
	}

	/**
	 * Builds the LIKE pattern, with the caller's own wildcards escaped — searching for
	 * {@code 100%} should look for the characters {@code 100%}, not for "100 followed by
	 * anything". Tags are stored lower-cased, and the query lower-cases title and notes, so the
	 * whole comparison is case-insensitive.
	 */
	private static String likePattern(String query) {
		String normalised = normalise(query);
		if (normalised == null) {
			return null;
		}
		String escaped = normalised.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

	/** Blank is the same as absent: {@code ?q=} from a cleared search box is not a filter. */
	private static String normalise(String value) {
		return (value == null || value.isBlank()) ? null : value.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Appends the id to whatever sort was asked for, making the order total.
	 *
	 * <p>The default sort on the controller is only a fallback — a {@code ?sort=} parameter
	 * replaces it wholesale, so {@code ?sort=title,asc} would otherwise order by a column full
	 * of ties. Each page is a separate query and the database is free to break those ties
	 * differently every time, so a bookmark can come back on two pages and another on none.
	 * Applying the tiebreaker here rather than in the annotation means no caller can drop it.
	 */
	private static Pageable totalOrder(Pageable pageable) {
		pageable.getSort().forEach((order) -> {
			if (!SORTABLE.contains(order.getProperty())) {
				throw new InvalidSortException(order.getProperty(), SORTABLE);
			}
		});
		// Descending, to match the direction of idx_bookmark_created_at — a btree is read
		// forwards or backwards, never one column each way.
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
	}

	@Transactional(readOnly = true)
	BookmarkResponse get(long id) {
		return BookmarkResponse.of(find(id));
	}

	BookmarkResponse create(CreateRequest request) {
		Bookmark bookmark = new Bookmark();
		bookmark.setUrl(request.url());
		bookmark.setTitle(request.title());
		bookmark.setNotes(request.notes());
		bookmark.setFavourite(Boolean.TRUE.equals(request.favourite()));
		bookmark.setTags(request.tags());
		return BookmarkResponse.of(this.repository.save(bookmark));
	}

	/** Null fields mean "not supplied", so a favourite toggle is a one-field request. */
	BookmarkResponse update(long id, UpdateRequest request) {
		Bookmark bookmark = find(id);
		if (request.url() != null) {
			bookmark.setUrl(request.url());
		}
		if (request.title() != null) {
			bookmark.setTitle(request.title());
		}
		if (request.notes() != null) {
			bookmark.setNotes(request.notes());
		}
		if (request.favourite() != null) {
			bookmark.setFavourite(request.favourite());
		}
		if (request.tags() != null) {
			bookmark.setTags(request.tags());
		}
		// The entity is managed, so the commit writes it without any save() call. The flush is
		// forced early only because @UpdateTimestamp fires on flush: mapping the response
		// before it would hand the caller the old updatedAt.
		this.repository.flush();
		return BookmarkResponse.of(bookmark);
	}

	void delete(long id) {
		// Checked first so deleting something that is already gone is a 404 rather than a
		// silent success. deleteById alone would not tell us either way.
		this.repository.delete(find(id));
	}

	private Bookmark find(long id) {
		return this.repository.findById(id).orElseThrow(() -> new BookmarkNotFoundException(id));
	}

}
