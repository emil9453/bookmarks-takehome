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
	 * Sort keys the API offers. An allow-list, because an unknown key otherwise reaches Spring
	 * Data and surfaces as a 500 for what is a client typo.
	 */
	private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "title", "favourite", "id");

	/**
	 * Maps to DTOs inside the transaction: the tag collection is lazy and {@code open-in-view} is
	 * off, so mapping in the controller would fail on the first bookmark that has tags.
	 */
	@Transactional(readOnly = true)
	Page<BookmarkResponse> search(String clientId, String query, String tag, Boolean favourite, Pageable pageable) {
		return this.repository.search(clientId, likePattern(query), normalise(tag), favourite, totalOrder(pageable))
			.map(BookmarkResponse::of);
	}

	/**
	 * The LIKE pattern, with the caller's own wildcards escaped: searching for {@code 100%} looks
	 * for those characters, not "100 followed by anything". Tags are stored lower-cased and the
	 * query lower-cases title and notes, so the comparison is case-insensitive throughout.
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
	 * <p>A {@code ?sort=} parameter replaces the controller's default wholesale, so
	 * {@code ?sort=title,asc} orders by a column full of ties. Each page is a separate query and
	 * the database may break those ties differently every time, putting a bookmark on two pages
	 * and another on none. Applied here rather than in the annotation so no caller can drop it.
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
	BookmarkResponse get(String clientId, long id) {
		return BookmarkResponse.of(find(clientId, id));
	}

	BookmarkResponse create(String clientId, CreateRequest request) {
		Bookmark bookmark = new Bookmark();
		bookmark.setClientId(clientId);
		bookmark.setUrl(request.url());
		bookmark.setTitle(request.title());
		bookmark.setNotes(request.notes());
		bookmark.setFavourite(Boolean.TRUE.equals(request.favourite()));
		bookmark.setTags(request.tags());
		return BookmarkResponse.of(this.repository.save(bookmark));
	}

	/** Null fields mean "not supplied", so a favourite toggle is a one-field request. */
	BookmarkResponse update(String clientId, long id, UpdateRequest request) {
		Bookmark bookmark = find(clientId, id);
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

	void delete(String clientId, long id) {
		// Checked first so deleting something that is already gone is a 404 rather than a
		// silent success. deleteById alone would not tell us either way.
		this.repository.delete(find(clientId, id));
	}

	/**
	 * Scoped by client, so another install's bookmark is a 404 rather than a 403 — the second
	 * would confirm the row exists, which is the one thing a caller guessing ids wants to know.
	 */
	private Bookmark find(String clientId, long id) {
		return this.repository.findByIdAndClientId(id, clientId)
			.orElseThrow(() -> new BookmarkNotFoundException(id));
	}

}
