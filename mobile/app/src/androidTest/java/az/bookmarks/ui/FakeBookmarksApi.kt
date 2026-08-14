package az.bookmarks.ui

import az.bookmarks.data.Bookmark
import az.bookmarks.data.BookmarkPatch
import az.bookmarks.data.BookmarksApi
import az.bookmarks.data.NewBookmark
import az.bookmarks.data.PageMeta
import az.bookmarks.data.PagedResponse
import java.io.IOException

/**
 * Stands in for the network so these tests are about the composition and nothing else. The real
 * client would make them slow, flaky, rate-limited, and — because `create` writes — would leave
 * test rows in the instance a reviewer is about to open.
 */
class FakeBookmarksApi(
    private val rows: List<Bookmark> = emptyList(),
) : BookmarksApi {

    val created = mutableListOf<NewBookmark>()

    /** Flipped by a test to make the next list call fail, which is how the error state is reached. */
    var failList = false

    override suspend fun list(
        page: Int,
        size: Int,
        q: String?,
        tag: String?,
        favourite: Boolean?,
    ): PagedResponse<Bookmark> {
        if (failList) throw IOException("no network")
        // Enough of the real filter to tell "nothing saved" apart from "nothing matched", which is
        // the distinction these tests exist to pin down.
        val matched = rows.filter { row ->
            (q == null || listOf(row.title, row.notes.orEmpty()).any { it.contains(q, true) } ||
                row.tags.any { it.contains(q, true) }) &&
                (tag == null || tag in row.tags) &&
                (favourite == null || row.favourite == favourite)
        }
        return PagedResponse(
            content = matched,
            page = PageMeta(size = size, number = page, totalElements = matched.size.toLong(), totalPages = 1),
        )
    }

    override suspend fun create(request: NewBookmark): Bookmark {
        created += request
        return bookmark(id = 99, title = request.title, url = request.url, tags = request.tags)
    }

    override suspend fun get(id: Long): Bookmark = rows.first { it.id == id }

    override suspend fun update(id: Long, request: BookmarkPatch): Bookmark = get(id)

    override suspend fun delete(id: Long) = Unit
}

fun bookmark(
    id: Long,
    title: String,
    url: String = "https://example.com/$id",
    tags: List<String> = emptyList(),
    notes: String? = null,
    favourite: Boolean = false,
) = Bookmark(
    id = id,
    url = url,
    title = title,
    tags = tags,
    notes = notes,
    favourite = favourite,
    createdAt = "2026-08-14T00:00:00Z",
    updatedAt = "2026-08-14T00:00:00Z",
)
