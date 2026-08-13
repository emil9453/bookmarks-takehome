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

    /** Internal rather than private so error bodies are parsed with the same configuration. */
    internal val json = Json { ignoreUnknownKeys = true }

    /**
     * The backend is on a free tier that sleeps after ~15 minutes idle, and the first request
     * afterwards waits for the container to start. OkHttp defaults to a 10-second read timeout,
     * which fails that request every time and makes the app look broken when it is the host
     * waking up.
     *
     * The numbers are measured, not guessed. A real cold start timed from this machine:
     * DNS 0.08s, TCP connect 0.09s, TLS 0.10s, **total 62.6s** — the connection is immediate and
     * the whole wait is the server thinking. A first draft of this used a 60s read timeout and
     * lost by 2.6 seconds, showing the error state on the first launch of every session.
     *
     * So: connect stays short because connecting is genuinely fast, and read carries real
     * headroom because that is the leg that waits. The cost is that a server which is actually
     * unreachable takes ~2 minutes to say so; on a warm server every response is ~0.3s, so this
     * ceiling is only ever reached on the cold path, where waiting is the correct behaviour.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val bookmarks: BookmarksApi = retrofit.create(BookmarksApi::class.java)
}
