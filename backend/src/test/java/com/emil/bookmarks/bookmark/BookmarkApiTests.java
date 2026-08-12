package com.emil.bookmarks.bookmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emil.bookmarks.bookmark.BookmarkDtos.BookmarkResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** The status-code contract, and the one piece of list logic that can silently lose rows. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class BookmarkApiTests {

	private static final String PATH = "/api/v1/bookmarks";

	@Autowired
	private RestTestClient rest;

	@Autowired
	private BookmarkRepository repository;

	@Autowired
	private BookmarkService service;

	@BeforeEach
	void clearDatabase() {
		this.repository.deleteAll();
	}

	@Test
	void createReturns201WithALocationAndDefaultsFavouriteToFalse() {
		this.rest.post()
			.uri(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"url":"https://kotlinlang.org","title":"Kotlin docs","tags":["Kotlin"]}""")
			.exchange()
			.expectStatus()
			.isCreated()
			.expectHeader()
			.exists("Location")
			.expectBody()
			.jsonPath("$.favourite")
			.isEqualTo(false)
			// Tags come back normalised and sorted, not as they were sent.
			.jsonPath("$.tags[0]")
			.isEqualTo("kotlin");
	}

	@Test
	void patchChangesOnlyTheFieldItWasSent() {
		long id = create("https://spring.io", "Spring");

		this.rest.patch()
			.uri(PATH + "/" + id)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"favourite":true}""")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.favourite")
			.isEqualTo(true)
			.jsonPath("$.title")
			.isEqualTo("Spring");
	}

	@Test
	void deleteReturns204AndThenTheBookmarkIsGone() {
		long id = create("https://spring.io", "Spring");

		this.rest.delete().uri(PATH + "/" + id).exchange().expectStatus().isNoContent().expectBody().isEmpty();

		this.rest.get().uri(PATH + "/" + id).exchange().expectStatus().isNotFound();
		this.rest.delete().uri(PATH + "/" + id).exchange().expectStatus().isNotFound();
	}

	/**
	 * Everything else about search is tested through the service. This is the only thing that
	 * pins the wiring: the query-parameter names, and each one reaching the argument it belongs
	 * to. Without it, swapping the controller's {@code q} and {@code tag} arguments — or
	 * passing nulls for all three — leaves the whole suite green while the phone's search box
	 * returns the entire table.
	 */
	@Test
	void theSearchAndFilterParametersAreWiredToTheRightArguments() {
		create("https://kotlinlang.org", "Kotlin guide", Set.of("android"));
		create("https://spring.io", "Spring guide", Set.of("java"));

		this.rest.get()
			.uri(PATH + "?q=kotlin")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.totalElements")
			.isEqualTo(1)
			.jsonPath("$.content[0].title")
			.isEqualTo("Kotlin guide");

		this.rest.get()
			.uri(PATH + "?tag=java")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.totalElements")
			.isEqualTo(1)
			.jsonPath("$.content[0].title")
			.isEqualTo("Spring guide");

		// A tag that exists as a search term but not as a tag: proves tag is filtering on tags
		// rather than quietly running as a second text search.
		this.rest.get()
			.uri(PATH + "?tag=kotlin")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.totalElements")
			.isEqualTo(0);
	}

	@Test
	void pageSizeIsCappedSoNobodyCanAskForTheWholeTable() {
		create("https://spring.io", "Spring");

		this.rest.get()
			.uri(PATH + "?size=100000")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.size")
			.isEqualTo(100);
	}

	@Test
	void anUnknownSortKeyIsARejectedRequestNotAServerError() {
		this.rest.get().uri(PATH + "?sort=bogus,asc").exchange().expectStatus().isBadRequest();
	}

	/**
	 * The regression that matters. Sorting by a column where every row ties leaves the database
	 * free to order those rows differently for each page, and each page is a separate query —
	 * so without a unique final sort key the same bookmark lands on two pages and another lands
	 * on none. Every title here is identical to force the ties.
	 */
	@Test
	void pagingATiedSortNeverRepeatsOrSkipsABookmark() {
		for (int i = 0; i < 25; i++) {
			create("https://example.com/" + i, "Same title");
		}

		List<Long> seen = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			this.service.search(null, null, null, PageRequest.of(page, 10, Sort.by(Sort.Direction.ASC, "title")))
				.forEach((bookmark) -> seen.add(bookmark.id()));
		}

		assertThat(seen).hasSize(25);
		assertThat(new HashSet<>(seen)).as("a bookmark appeared on two pages, or on none").hasSize(25);
	}

	private long create(String url, String title) {
		return create(url, title, Set.of("tag"));
	}

	private long create(String url, String title, Set<String> tags) {
		return this.service.create(new BookmarkDtos.CreateRequest(url, title, tags, null, null)).id();
	}

}
