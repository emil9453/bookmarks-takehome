package com.emil.bookmarks.bookmark;

import java.util.List;
import java.util.Set;

import com.emil.bookmarks.bookmark.BookmarkDtos.BookmarkResponse;
import com.emil.bookmarks.bookmark.BookmarkDtos.CreateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ranking is the one piece of real logic in this backend, so it is the one place a regression
 * would actually hurt. Weights are title 3, tag 2, notes 1, added together.
 */
@SpringBootTest
class BookmarkSearchTests {

	private static final String CLIENT = "search-tests";

	@Autowired
	private BookmarkService service;

	@Autowired
	private BookmarkRepository repository;

	@BeforeEach
	void clearDatabase() {
		this.repository.deleteAll();
	}

	/**
	 * Saved best-first on purpose. The fallback order when nothing is ranked ends {@code id
	 * desc}, so fixtures saved worst-first would come back in the expected order whether the
	 * ranking worked or not — this ordering fails outright if the score is ignored.
	 */
	@Test
	void ranksTitleAboveTagAboveNotes() {
		long titleOnly = save("Kotlin guide", Set.of("misc"), "nothing relevant");
		long tagOnly = save("Some guide", Set.of("kotlin"), "nothing relevant");
		long notesOnly = save("Some guide", Set.of("misc"), "all about kotlin");

		assertThat(idsFrom(search("kotlin"))).containsExactly(titleOnly, tagOnly, notesOnly);
	}

	@Test
	void matchingInTwoPlacesOutranksMatchingInOne() {
		// Again best-first, so recency alone cannot reproduce the expected answer.
		long titleAndTag = save("Kotlin book", Set.of("kotlin"), "nothing relevant");
		long titleOnly = save("Kotlin guide", Set.of("misc"), "nothing relevant");

		// 3 + 2 beats 3 — the scores add up rather than stopping at the first field that hits.
		assertThat(idsFrom(search("kotlin"))).containsExactly(titleAndTag, titleOnly);
	}

	/**
	 * Title alone scores 3, and tag plus notes also scores 3. Whichever way that tie is settled,
	 * it must be settled by a rule — before the breadth tiebreak existed, the winner was
	 * whichever happened to be saved later.
	 *
	 * <p>This and the test below are a pair, and the pair is the assertion: the same two
	 * bookmarks saved in both orders have to produce the same answer. Only one of the two can
	 * fail for any given bug, since the other's expected answer coincides with the fallback
	 * order — that is what makes them a control and a test rather than a duplicate.
	 */
	@Test
	void whenScoresCollideTheBookmarkMatchingInMorePlacesWins() {
		long titleOnly = save("Kotlin guide", Set.of("misc"), "nothing relevant");
		long tagAndNotes = save("Some guide", Set.of("kotlin"), "all about kotlin");

		assertThat(idsFrom(search("kotlin"))).containsExactly(tagAndNotes, titleOnly);
	}

	@Test
	void theCollisionIsSettledTheSameWayWhicheverOrderTheyWereSavedIn() {
		long tagAndNotes = save("Some guide", Set.of("kotlin"), "all about kotlin");
		long titleOnly = save("Kotlin guide", Set.of("misc"), "nothing relevant");

		assertThat(idsFrom(search("kotlin"))).containsExactly(tagAndNotes, titleOnly);
	}

	/**
	 * The determinism criterion applies to the ranked path, where the {@code order by} lives
	 * inside the query and Spring Data appends the unique key behind it. Every bookmark here
	 * scores identically, so nothing but that appended id keeps the pages apart.
	 */
	@Test
	void pagingARankedSearchFullOfTiesNeverRepeatsOrSkipsABookmark() {
		for (int i = 0; i < 25; i++) {
			save("Tied kotlin title", Set.of("kotlin"), "kotlin in the notes too");
		}

		List<Long> seen = new java.util.ArrayList<>();
		for (int page = 0; page < 3; page++) {
			seen.addAll(idsFrom(this.service.search(CLIENT, "kotlin", null, null, PageRequest.of(page, 10))));
		}

		assertThat(seen).hasSize(25);
		assertThat(new java.util.HashSet<>(seen)).as("a bookmark appeared on two pages, or on none").hasSize(25);
	}

	@Test
	void theBestResultsAreOnPageOneNotTheBestOfWhicheverPageYouLandOn() {
		// Twenty weak matches created first, so recency alone would push them above the strong
		// ones. Only ranking can put the two title matches on a page of five.
		for (int i = 0; i < 20; i++) {
			save("Filler " + i, Set.of("misc"), "mentions kotlin in passing");
		}
		long strong = save("Kotlin guide", Set.of("kotlin"), "nothing relevant");
		long alsoStrong = save("Kotlin book", Set.of("misc"), "nothing relevant");

		List<Long> firstPage = idsFrom(this.service.search(CLIENT, "kotlin", null, null, PageRequest.of(0, 5)));

		assertThat(firstPage).startsWith(strong, alsoStrong);
	}

	@Test
	void searchAndTagAndFavouriteCombine() {
		save("Kotlin guide", Set.of("android"), null);
		long wanted = favourite(save("Kotlin book", Set.of("android"), null));
		favourite(save("Java book", Set.of("android"), null));
		favourite(save("Kotlin notes", Set.of("desktop"), null));

		assertThat(idsFrom(this.service.search(CLIENT, "kotlin", "android", true, Pageable.ofSize(20))))
			.containsExactly(wanted);
	}

	@Test
	void aFilterOnItsOwnWorksWithNoSearchTerm() {
		save("Kotlin guide", Set.of("android"), null);
		long starred = favourite(save("Spring", Set.of("java"), null));

		assertThat(idsFrom(this.service.search(CLIENT, null, null, true, Pageable.ofSize(20)))).containsExactly(starred);
		assertThat(idsFrom(this.service.search(CLIENT, null, "java", null, Pageable.ofSize(20)))).containsExactly(starred);
	}

	@Test
	void aTagFilterIsCaseInsensitiveBecauseTagsAreStoredNormalised() {
		long id = save("Kotlin guide", Set.of("Android"), null);

		assertThat(idsFrom(this.service.search(CLIENT, null, "ANDROID", null, Pageable.ofSize(20)))).containsExactly(id);
	}

	@Test
	void matchingNothingIsAnEmptyListNotAnError() {
		save("Kotlin guide", Set.of("android"), "notes");

		assertThat(search("nothingmatchesthis")).isEmpty();
	}

	@Test
	void aWildcardTypedIntoTheSearchBoxIsSearchedForLiterally() {
		long literal = save("Save 100% of the time", Set.of("misc"), null);
		// Has to start with "100" as well, or it is excluded by the literal characters alone
		// and the test passes whether the % is escaped or not.
		save("100 ways to save", Set.of("misc"), null);

		// Escaped, the pattern needs the characters "100%". Unescaped it becomes %100%%, whose
		// trailing wildcard also matches the second bookmark.
		assertThat(idsFrom(search("100%"))).containsExactly(literal);
	}

	@Test
	void aBlankSearchTermIsNotAFilter() {
		save("Kotlin guide", Set.of("android"), null);
		save("Spring", Set.of("java"), null);

		assertThat(search("   ")).hasSize(2);
	}

	private List<BookmarkResponse> search(String query) {
		return this.service.search(CLIENT, query, null, null, Pageable.ofSize(20)).getContent();
	}

	private static List<Long> idsFrom(List<BookmarkResponse> results) {
		return results.stream().map(BookmarkResponse::id).toList();
	}

	private static List<Long> idsFrom(org.springframework.data.domain.Page<BookmarkResponse> results) {
		return idsFrom(results.getContent());
	}

	private long save(String title, Set<String> tags, String notes) {
		return this.service.create(CLIENT, new CreateRequest("https://example.com/" + title.hashCode(), title, tags, notes,
				null)).id();
	}

	private long favourite(long id) {
		this.service.update(CLIENT, id, new BookmarkDtos.UpdateRequest(null, null, null, null, true));
		return id;
	}

}
