package az.bookmarks.ui

import az.bookmarks.data.PageMeta
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The paging arithmetic and the error copy. The state machine that uses them is covered in
 * [BookmarkListViewModelTest].
 */
class BookmarkListStateTest {

    private fun meta(number: Int, totalPages: Int) =
        PageMeta(size = 20, number = number, totalElements = 0, totalPages = totalPages)

    @Test
    fun hasMoreIsTrueWhileLaterPagesExist() {
        // Pages are zero-based: page 0 of 3 has more, and so does page 1.
        assertTrue(meta(number = 0, totalPages = 3).hasMore)
        assertTrue(meta(number = 1, totalPages = 3).hasMore)
    }

    @Test
    fun hasMoreIsFalseOnTheLastPage() {
        // The off-by-one that would ask the backend for a page past the end forever.
        assertFalse(meta(number = 2, totalPages = 3).hasMore)
        assertFalse(meta(number = 0, totalPages = 1).hasMore)
    }

    @Test
    fun hasMoreIsFalseWhenThereIsNothing() {
        assertFalse(meta(number = 0, totalPages = 0).hasMore)
    }

    @Test
    fun unreachableServerIsDistinguishedFromAServerError() {
        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            IOException("no route to host").toMessage(),
        )

        val serverError = HttpException(
            Response.error<Unit>(503, "".toResponseBody("application/json".toMediaType())),
        )
        assertTrue(serverError.toMessage().contains("503"))
    }
}
