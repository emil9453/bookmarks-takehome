package com.emil.bookmarks.bookmark;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * A saved link. The schema is owned by Flyway, not by this class — Hibernate runs with
 * {@code ddl-auto: validate}, so the two disagreeing stops the application from starting.
 */
@Entity
@Table(name = "bookmark")
public class Bookmark {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Which install this bookmark belongs to — a random id the client generates once and sends as
	 * {@code X-Client-Id}. It is not authentication: the value is self-asserted, so it separates
	 * collections without protecting them. Accounts are the upgrade when the data is worth
	 * protecting rather than merely separating.
	 */
	@Column(name = "client_id", nullable = false, length = 64)
	private String clientId;

	@Column(nullable = false, length = 2048)
	private String url;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 2000)
	private String notes;

	@Column(nullable = false)
	private boolean favourite;

	/**
	 * A plain set of strings rather than a Tag entity: nothing here needs a tag to exist on its
	 * own, be renamed globally, or carry attributes of its own.
	 *
	 * <p>{@code @BatchSize} is what keeps listing bookmarks off the N+1 path — Hibernate fetches
	 * the tags for up to 50 bookmarks in one {@code where bookmark_id in (…)} query instead of
	 * one query per row. A fetch join would also collapse it to one query, but it multiplies
	 * rows by tags, which forces the page window to be applied in memory instead of in the
	 * database.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "bookmark_tag", joinColumns = @JoinColumn(name = "bookmark_id"))
	@Column(name = "tag", nullable = false, length = 50)
	@BatchSize(size = 50)
	private Set<String> tags = new LinkedHashSet<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return this.id;
	}

	public String getClientId() {
		return this.clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getNotes() {
		return this.notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public boolean isFavourite() {
		return this.favourite;
	}

	public void setFavourite(boolean favourite) {
		this.favourite = favourite;
	}

	public Set<String> getTags() {
		return this.tags;
	}

	/**
	 * Tags are stored lower-cased and trimmed, so {@code Kotlin} and {@code kotlin} are one tag
	 * rather than two rows, and BOO-9's tag filter stays a plain indexed equality match instead
	 * of a {@code lower(tag) = ?} that could not use the index.
	 */
	public void setTags(Set<String> tags) {
		// Mutate the contents rather than replace the instance: the collection Hibernate handed
		// out diffs against its own snapshot, so re-setting the same tags emits no SQL at all,
		// where a fresh instance would delete every row and reinsert it.
		this.tags.clear();
		if (tags == null) {
			return;
		}
		tags.stream()
			.map((tag) -> tag.toLowerCase(Locale.ROOT).trim())
			// A whitespace-only tag would otherwise become an empty-string row, and then show
			// up as a real tag everywhere tags are listed or filtered on.
			.filter((tag) -> !tag.isEmpty())
			.forEach(this.tags::add);
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
