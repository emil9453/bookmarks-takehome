package az.bookmarks.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The wire types are used straight through to the UI. There is no separate domain model and no
 * mapping layer: there is one shape, no behaviour to protect, and a second identical set of
 * classes would be ceremony. If the API and the screens ever want to disagree, this is where a
 * mapper goes.
 */
@Serializable
data class Bookmark(
    val id: Long,
    val url: String,
    val title: String,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val favourite: Boolean = false,
    // ponytail: kept as the raw ISO-8601 string. Nothing formats a date yet, and parsing it
    // would mean adding kotlinx-datetime for a field that is currently only carried around.
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Spring's `PagedModel` envelope: `{ "content": [...], "page": { ... } }`. The backend returns
 * this rather than a raw `Page` precisely so the JSON is a stable contract to parse against.
 */
@Serializable
data class PagedResponse<T>(
    val content: List<T>,
    val page: PageMeta,
)

@Serializable
data class PageMeta(
    val size: Int,
    val number: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    /** Pages are zero-based, so page 0 of 3 has more; page 2 of 3 does not. */
    val hasMore: Boolean get() = number + 1 < totalPages
}

interface BookmarksApi {

    @GET("api/v1/bookmarks")
    suspend fun list(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PagedResponse<Bookmark>
}
