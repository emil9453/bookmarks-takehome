package az.bookmarks

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The base URL is a build-type config value, so a broken buildConfigField would only surface
 * at the first network call. This fails at build time instead.
 */
class BuildConfigTest {

    @Test
    fun baseUrlIsHttpsAndEndsWithSlash() {
        val url = BuildConfig.BASE_URL
        assertTrue("BASE_URL must be https, was: $url", url.startsWith("https://"))
        // Retrofit rejects a base URL without a trailing slash.
        assertTrue("BASE_URL must end with '/', was: $url", url.endsWith("/"))
    }
}
