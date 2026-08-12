package com.emil.bookmarks.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The document itself is generated from the controllers, so it cannot drift away from them.
 * This adds only the things reflection cannot know — what the API is for, and the two
 * behaviours a caller has to understand before the endpoint list makes sense.
 */
@Configuration
class OpenApiConfig {

	@Bean
	OpenAPI bookmarksApi() {
		return new OpenAPI().info(new Info().title("Bookmarks API")
			.version("v1")
			.description("""
					A read-it-later service: save a link with a title, tags and notes, then \
					list, search and favourite them.

					**Search is ranked.** `?q=` looks in the title, the tags and the notes, \
					and the matches are scored — title 3, tag 2, notes 1 — and added together, \
					so a bookmark matching in the title *and* a tag outranks one matching only \
					the title. Where scores tie, the bookmark matching in more places wins, \
					then the more recent one. `q`, `tag` and `favourite` combine freely.

					**Updates are partial.** `PATCH` changes only the fields you send, which is \
					what makes the favourite toggle a one-field request rather than a round \
					trip of the whole bookmark. A field you omit is left alone — so to clear \
					the notes, send an empty string rather than null.

					**Errors are RFC 9457 problem details**, the same shape for every failure. \
					A validation failure adds an `errors` object keyed by field name.
					"""));
	}

}
