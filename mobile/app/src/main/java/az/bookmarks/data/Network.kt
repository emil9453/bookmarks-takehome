package az.bookmarks.data

import az.bookmarks.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Hand-written wiring instead of a DI framework.
 *
 * ponytail: there are three things to construct — a Json, an OkHttpClient and a Retrofit. Hilt
 * would add a compiler plugin, a generated component and an Application subclass to save
 * nothing. Add it at roughly five injectables, or at the first multi-module split, and the seam
 * is this object.
 *
 * BASE_URL comes from the build type (see app/build.gradle.kts), never from a call site.
 */
object Network {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The backend is on a free tier that sleeps after ~15 minutes idle, and the first request
     * afterwards waits for a container to start — around 50 seconds. OkHttp's defaults are a
     * 10-second read timeout, so with them the first request of every session fails and the app
     * looks broken when it is the host waking up. The loading state has to be able to outlast
     * a cold start, so these are sized for it rather than for a warm server.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val bookmarks: BookmarksApi = retrofit.create(BookmarksApi::class.java)
}
