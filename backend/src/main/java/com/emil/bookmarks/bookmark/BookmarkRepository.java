package com.emil.bookmarks.bookmark;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

	/**
	 * Text search, tag filter and favourites filter in one query, each skipped by its own
	 * {@code :param is null} guard.
	 *
	 * <p>JPQL rather than native SQL for that reason: Hibernate binds a typed null, where a bare
	 * SQL null makes Postgres refuse to infer the parameter's type — a query that works on H2 and
	 * fails once deployed.
	 *
	 * <p><b>Ranking, in two keys.</b> The weighted score first: title 3, tag 2, notes 1, summed
	 * rather than stopping at the first field that hits, so title <em>and</em> tag outranks title
	 * alone. Then the number of fields that matched, because the weights collide — a title match
	 * alone scores 3 and so does tag plus notes. Scoring runs in the database, so page one holds
	 * the best results overall rather than the best of whichever rows the page window caught.
	 *
	 * <p>Spring Data appends the {@code Pageable}'s own ordering after both keys, which is where
	 * the recency tiebreak and the final unique id come from: scores are small integers that tie
	 * constantly, and a tie with no unique key underneath puts a bookmark on two pages or none.
	 *
	 * <p>No {@code countQuery}: Spring Data derives one from this query's {@code where} clause,
	 * so it cannot drift out of step the way a hand-written copy would.
	 */
	@Query("""
			select b from Bookmark b
			where b.clientId = :clientId
			  and (:text is null
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
	Page<Bookmark> search(@Param("clientId") String clientId, @Param("text") String text, @Param("tag") String tag,
			@Param("favourite") Boolean favourite, Pageable pageable);

	/**
	 * Single-bookmark lookup, scoped the same way. Another client's id has to come back empty
	 * rather than forbidden — a 403 would confirm the row exists, which is exactly what a caller
	 * guessing ids is trying to learn.
	 */
	Optional<Bookmark> findByIdAndClientId(Long id, String clientId);

}
