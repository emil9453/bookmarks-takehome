package az.bookmarks.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
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
    // Kept as the raw ISO-8601 string. Nothing formats a date yet, and parsing it
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

    /**
     * One endpoint for the plain list, the search and both filters — they combine freely, and
     * Retrofit omits a null query parameter, so absent means "do not filter on this".
     *
     * With `q` set the backend returns results ranked best-first (title 3 + tag 2 + notes 1).
     * The phone renders them in the order they arrive and never re-sorts: the ranking is the
     * most interesting thing in the project, and re-sorting here would throw it away.
     */
    @GET("api/v1/bookmarks")
    suspend fun list(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("q") q: String? = null,
        @Query("tag") tag: String? = null,
        @Query("favourite") favourite: Boolean? = null,
    ): PagedResponse<Bookmark>

    @GET("api/v1/bookmarks/{id}")
    suspend fun get(@Path("id") id: Long): Bookmark

    @POST("api/v1/bookmarks")
    suspend fun create(@Body request: NewBookmark): Bookmark

    /**
     * Partial update: only the fields present are changed, which is what makes the favourite
     * toggle a one-field request rather than sending the whole bookmark back and risking
     * overwriting something changed elsewhere.
     */
    @PATCH("api/v1/bookmarks/{id}")
    suspend fun update(@Path("id") id: Long, @Body request: BookmarkPatch): Bookmark

    @DELETE("api/v1/bookmarks/{id}")
    suspend fun delete(@Path("id") id: Long)
}

/**
 * No default values on purpose. `Json` omits any property that equals its declared default, so a
 * default here would silently drop the field from the body — fine for a patch, wrong for a
 * create, where "no tags" and "tags absent" should not be the same request.
 */
@Serializable
data class NewBookmark(
    val url: String,
    val title: String,
    val tags: List<String>,
    val notes: String?,
    val favourite: Boolean,
)

/**
 * Every field is null by default and nulls are omitted from the body, so this sends only what is
 * being changed — the backend reads absent as "leave this alone".
 */
@Serializable
data class BookmarkPatch(
    val url: String? = null,
    val title: String? = null,
    val tags: List<String>? = null,
    val notes: String? = null,
    val favourite: Boolean? = null,
)
