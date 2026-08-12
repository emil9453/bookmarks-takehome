package az.bookmarks.data

import az.bookmarks.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Hand-written wiring instead of a DI framework.
 *
 * ponytail: there are two things to construct — a Json and a Retrofit. Hilt would add a
 * compiler plugin, a generated component and an Application subclass to save nothing. Add it
 * at roughly five injectables, or at the first multi-module split, and the seam is this object.
 *
 * BASE_URL comes from the build type (see app/build.gradle.kts), never from a call site.
 */
object Network {
    private val json = Json { ignoreUnknownKeys = true }

    // No `by lazy` — the enclosing object is already initialised on first access, and this is
    // the only reason to touch it.
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
