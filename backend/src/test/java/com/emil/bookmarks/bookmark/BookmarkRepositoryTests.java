package com.emil.bookmarks.bookmark;

import java.util.List;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one thing about this schema that can silently regress: dropping {@code @BatchSize} from
 * the tag collection still passes every functional test, it just quietly issues one extra query
 * per row.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BookmarkRepositoryTests {

	@Autowired
	private BookmarkRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void listingBookmarksDoesNotQueryTagsOncePerRow() {
		for (int i = 0; i < 10; i++) {
			Bookmark bookmark = new Bookmark();
			bookmark.setUrl("https://example.com/" + i);
			bookmark.setTitle("Bookmark " + i);
			bookmark.setTags(Set.of("kotlin", "android"));
			this.repository.save(bookmark);
		}
		this.entityManager.flush();
		// Without this the bookmarks are still in the persistence context and reading their
		// tags would hit no database at all, so the test would pass whatever the mapping says.
		this.entityManager.clear();

		Statistics statistics = this.entityManager.getEntityManagerFactory()
			.unwrap(SessionFactory.class)
			.getStatistics();
		statistics.clear();

		// Through search(), because that is the only list path the API actually uses — findAll
		// could keep its batching forever while the endpoint people hit lost it.
		List<Bookmark> page = this.repository.search(null, null, null, PageRequest.of(0, 10)).getContent();
		page.forEach((bookmark) -> bookmark.getTags().size());

		assertThat(page).hasSize(10);
		// One query for the page, one for its total, one batched fetch for every row's tags.
		// One query per row instead would be 12.
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
	}

	@Test
	void tagsAreNormalisedSoOneTagIsNotStoredTwice() {
		Bookmark bookmark = new Bookmark();
		bookmark.setTags(Set.of("Kotlin", " kotlin ", "ANDROID"));

		assertThat(bookmark.getTags()).containsExactlyInAnyOrder("kotlin", "android");
	}

	@Test
	void clearingTagsWithNullLeavesTheBookmarkUsable() {
		Bookmark bookmark = new Bookmark();
		bookmark.setTags(Set.of("kotlin"));

		bookmark.setTags(null);

		assertThat(bookmark.getTags()).isEmpty();
	}

}
