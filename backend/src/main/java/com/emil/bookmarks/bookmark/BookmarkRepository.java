package com.emil.bookmarks.bookmark;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

	/**
	 * Text search, tag filter and favourites filter in one query — any of them, in any
	 * combination, or none at all, in which case this is the plain list.
	 *
	 * <p>Each filter is skipped by its own {@code :param is null} guard. Written in JPQL rather
	 * than native SQL for exactly this reason: Hibernate binds a typed null, where a bare SQL
	 * null makes Postgres complain that it cannot determine the parameter's type — a query that
	 * works locally and fails once deployed.
	 *
	 * <p><b>Ranking, in two keys.</b> First the weighted score: title 3, tag 2, notes 1, added
	 * together rather than stopping at the first field that hits, which is what makes a
	 * bookmark matching the title <em>and</em> a tag outrank one matching only the title.
	 *
	 * <p>Then the number of fields that matched at all, because the weights collide: a title
	 * match alone scores 3, and so does a tag plus a notes match. Without a second key that tie
	 * falls through to recency, so which one a reviewer sees first depends on the order the two
	 * were saved in. Breaking it on breadth keeps the promise that matching in more places
	 * ranks higher, and makes the answer a rule rather than an accident of insertion order.
	 *
	 * <p>Scoring happens in the database, not in Java, so page one holds the best results
	 * overall rather than the best of whichever rows the page window happened to catch.
	 *
	 * <p>These two keys are not the whole sort. Spring Data appends the {@code Pageable}'s own
	 * ordering after them, which is where the recency tiebreak and the final unique id come
	 * from — scores are small integers that tie constantly, and ties with no unique key
	 * underneath let a bookmark appear on two pages or on none.
	 *
	 * <p>No {@code countQuery} here on purpose: Spring Data derives one from this query's own
	 * {@code where} clause, which cannot then drift out of step with it the way a hand-written
	 * copy of the same eight lines would.
	 */
	@Query("""
			select b from Bookmark b
			where (:text is null
			        or lower(b.title) like :text escape '\\'
			        or lower(b.notes) like :text escape '\\'
			        or exists (select t from b.tags t where t like :text escape '\\'))
			  and (:tag is null or :tag member of b.tags)
			  and (:favourite is null or b.favourite = :favourite)
			order by
			  (case when lower(b.title) like :text escape '\\' then 3 else 0 end)
			+ (case when exists (select t from b.tags t where t like :text escape '\\') then 2 else 0 end)
			+ (case when lower(b.notes) like :text escape '\\' then 1 else 0 end) desc,
			  (case when lower(b.title) like :text escape '\\' then 1 else 0 end)
			+ (case when exists (select t from b.tags t where t like :text escape '\\') then 1 else 0 end)
			+ (case when lower(b.notes) like :text escape '\\' then 1 else 0 end) desc
			""")
	Page<Bookmark> search(@Param("text") String text, @Param("tag") String tag,
			@Param("favourite") Boolean favourite, Pageable pageable);

}
