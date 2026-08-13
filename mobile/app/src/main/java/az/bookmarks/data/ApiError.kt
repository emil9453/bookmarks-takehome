package az.bookmarks.data

import kotlinx.serialization.Serializable
import retrofit2.HttpException

/**
 * The error half of the API, RFC 9457. The backend returns this shape for every failure — which
 * is exactly why there is no success envelope: only the errors carry the extra structure, so the
 * success models stay plain.
 *
 * `errors` is the backend's own addition for failed validation, keyed by the JSON field name the
 * client sent, which is what lets a message land under the right input box rather than in a
 * generic "save failed".
 */
@Serializable
data class ProblemDetail(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: Map<String, String> = emptyMap(),
)

/**
 * Pulls the problem detail out of a failed call, or null when there is not one to pull.
 *
 * Null is a real case, not defensive padding: a 502 from the host's edge proxy while the free
 * tier wakes up is an HTML page, and a read timeout has no body at all. Callers fall back to
 * their own message.
 *
 * The body can only be consumed once, so this is called once per failure.
 */
internal fun HttpException.problemDetail(): ProblemDetail? = runCatching {
    response()
        ?.errorBody()
        ?.string()
        ?.takeIf(String::isNotBlank)
        ?.let { body -> Network.json.decodeFromString<ProblemDetail>(body) }
}.getOrNull()
