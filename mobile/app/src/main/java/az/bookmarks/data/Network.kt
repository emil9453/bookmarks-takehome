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
 * There are three things to construct — a Json, an OkHttpClient and a Retrofit. Hilt
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
     * This install's id, sent on every request so the backend returns this phone's bookmarks and
     * not everybody's. Set once from [MainActivity.onCreate] — before `setContent`, so it is in
     * place before any ViewModel exists, let alone issues a request.
     *
     * An interceptor rather than a parameter on each [BookmarksApi] method: the header belongs to
     * the transport, and threading it through five signatures would put it in the call sites too,
     * where one omission is a silent read of the wrong collection.
     */
    @Volatile
    var clientId: String = ""

    /**
     * These were once 30/120/150, sized for a free hosting tier that slept after 15 minutes idle
     * and took a measured 62.6s to wake — a 60s read timeout lost that race by 2.6 seconds and
     * showed the error state on the first launch of every session. The backend now runs on a VPS
     * that does not sleep and answers in ~250ms, so that headroom no longer buys anything and
     * actively costs: a server that is genuinely down took ~2 minutes to say so.
     *
     * Still well above OkHttp's 10s read default, because the ceiling that matters now is a slow
     * mobile network rather than a waking container.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("X-Client-Id", clientId).build(),
            )
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val bookmarks: BookmarksApi = retrofit.create(BookmarksApi::class.java)
}
