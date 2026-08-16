package com.emil.bookmarks.bookmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emil.bookmarks.bookmark.BookmarkDtos.BookmarkResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

	/**
	 * The fixtures go in through the service, the assertions come back over HTTP without an
	 * {@code X-Client-Id} header — so this has to be the same bucket the controller falls back to,
	 * or every one of these tests asserts against an empty list.
	 */
	private static final String CLIENT = "shared";

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
			this.service.search(CLIENT, null, null, null, PageRequest.of(page, 10, Sort.by(Sort.Direction.ASC, "title")))
				.forEach((bookmark) -> seen.add(bookmark.id()));
		}

		assertThat(seen).hasSize(25);
		assertThat(new HashSet<>(seen)).as("a bookmark appeared on two pages, or on none").hasSize(25);
	}

	/**
	 * Both spellings filter. This is not tidiness: an unrecognised query parameter is not an
	 * error in Spring MVC, so before the alias existed {@code ?favorite=true} returned 200 with
	 * every bookmark in it — the filter dropped, and nothing anywhere saying so. A silently wrong
	 * answer is worse than a rejected request, and the American spelling is the one a client
	 * reaches for first.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "favourite", "favorite" })
	void bothSpellingsOfFavouriteFilter(String parameter) {
		this.service.update(CLIENT, create("https://example.com/starred", "Starred"),
				new BookmarkDtos.UpdateRequest(null, null, null, null, true));
		create("https://example.com/plain", "Plain");

		this.rest.get()
			.uri(PATH + "?" + parameter + "=true")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.totalElements")
			.isEqualTo(1);
	}

	/**
	 * Both spellings in the body, too. The filter took both long before this did, which made the
	 * gap easy to miss: the specification names the field {@code favorite}, unknown fields are a
	 * 400 here, and so a create request copied straight out of it was rejected outright while the
	 * search URL from the same page worked. A reviewer reaching for the documented spelling is the
	 * likeliest first caller there is.
	 *
	 * <p>Create and update are separate records, so the alias on one says nothing about the other.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "favourite", "favorite" })
	void bothSpellingsOfFavouriteInTheBody(String field) {
		this.rest.post()
			.uri(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"url":"https://example.com","title":"Starred","%s":true}""".formatted(field))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.favourite")
			.isEqualTo(true);

		this.rest.patch()
			.uri(PATH + "/" + create("https://example.com/plain", "Plain"))
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"%s":true}""".formatted(field))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.favourite")
			.isEqualTo(true);
	}

	/**
	 * Both base paths reach the same resource. {@code /api/v1/bookmarks} is canonical, but
	 * {@code /bookmarks} is the path the API was specified with — without the second mapping a
	 * caller pasting that one gets a 404 for a resource that is there.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/bookmarks", "/bookmarks" })
	void bothBasePathsReachTheResource(String base) {
		long id = create("https://spring.io", "Spring");

		this.rest.get()
			.uri(base + "?q=spring")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.totalElements")
			.isEqualTo(1);

		this.rest.get().uri(base + "/" + id).exchange().expectStatus().isOk();
	}

	/**
	 * The reason the header exists. Before it, every install read one global list, so the first
	 * two people to open the app saw each other's bookmarks.
	 *
	 * <p>Both halves are needed. The list assertion alone passes with the filter applied only to
	 * the search query, which still leaves anyone able to read, edit and delete another client's
	 * bookmark by its id — and ids are sequential, so guessing one is not a feat.
	 */
	@Test
	void oneClientNeverSeesAnotherClientsBookmarks() {
		long theirs = create("https://example.com/theirs", "Theirs");

		this.rest.get()
			.uri(PATH)
			.header("X-Client-Id", "somebody-else")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.page.totalElements")
			.isEqualTo(0);

		// 404 rather than 403: the second confirms the row is there, which is exactly what a
		// caller walking the id sequence is trying to find out.
		this.rest.get().uri(PATH + "/" + theirs).header("X-Client-Id", "somebody-else").exchange()
			.expectStatus().isNotFound();
		this.rest.delete().uri(PATH + "/" + theirs).header("X-Client-Id", "somebody-else").exchange()
			.expectStatus().isNotFound();

		// And it is still there for the client that owns it.
		this.rest.get().uri(PATH + "/" + theirs).exchange().expectStatus().isOk();
	}

	/**
	 * The column is {@code varchar(64)}. Without the constraint on the parameter an over-long
	 * header reaches the insert and comes back a 500 — a client mistake reported as a server
	 * fault, and one that only shows up once the request has already touched the database.
	 */
	@Test
	void anOverLongClientIdIsARejectedRequestNotAServerError() {
		this.rest.get()
			.uri(PATH)
			.header("X-Client-Id", "x".repeat(65))
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	private long create(String url, String title) {
		return create(url, title, Set.of("tag"));
	}

	private long create(String url, String title, Set<String> tags) {
		return this.service.create(CLIENT, new BookmarkDtos.CreateRequest(url, title, tags, null, null)).id();
	}

}
