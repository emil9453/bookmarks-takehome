package com.emil.bookmarks.bookmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Bad input has to come back as a 400 that says which field was wrong — a rejection the phone
 * cannot attribute to a field is no better than a generic failure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class BookmarkValidationTests {

	private static final String PATH = "/api/v1/bookmarks";

	@Autowired
	private RestTestClient rest;

	@Test
	void aBlankTitleIsRejectedAndNamed() {
		post("""
				{"url":"https://example.com","title":"   "}""").expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.errors.title")
			.isEqualTo("must not be blank");
	}

	@Test
	void aMalformedLinkIsRejectedAndNamed() {
		post("""
				{"url":"not a link","title":"Fine"}""").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.url")
			.isEqualTo("must be a valid http or https link");
	}

	@Test
	void aLinkThatIsNotAWebPageIsRejected() {
		// Structurally a valid URL, which is why @URL alone would let it through.
		post("""
				{"url":"file:///etc/passwd","title":"Fine"}""").expectStatus().isBadRequest();
	}

	@Test
	void everyFieldHasALengthLimit() {
		post("""
				{"url":"https://example.com","title":"%s"}""".formatted("t".repeat(201))).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.title")
			.exists();

		post("""
				{"url":"https://example.com","title":"Fine","notes":"%s"}""".formatted("n".repeat(2001)))
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.notes")
			.exists();

		post("""
				{"url":"https://example.com","title":"Fine","tags":["%s"]}""".formatted("g".repeat(51)))
			.expectStatus()
			.isBadRequest()
			.expectBody()
			// The constraint fails on a collection element; the client still gets "tags", the
			// field it sent, not "tags[].<list element>".
			.jsonPath("$.errors.tags")
			.exists();
	}

	@Test
	void everyBadFieldIsReportedNotJustTheFirst() {
		post("""
				{"url":"not a link","title":""}""").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.detail")
			.isEqualTo("The request has 2 invalid fields.")
			.jsonPath("$.errors.url")
			.exists()
			.jsonPath("$.errors.title")
			.exists();
	}

	@Test
	void aTypoInAFieldNameIsRejectedAndTheFieldIsNamed() {
		post("""
				{"url":"https://example.com","title":"Fine","favorite":true}""").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.detail")
			.isEqualTo("Unknown field 'favorite'.");
	}

	@Test
	void malformedJsonIsRejectedWithoutQuotingItBack() {
		post("{\"url\": ").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.detail")
			.isEqualTo("The request body is not valid JSON.");
	}

	/**
	 * These are the framework's own errors, not the application's. A previous attempt at the
	 * error handler ordered a catch-all ahead of Spring's own and turned every one of them into
	 * a 500 — a well-formed problem detail with entirely the wrong status, which no test
	 * asserting only the happy path would have noticed.
	 */
	@Test
	void frameworkErrorsKeepTheirOwnStatusRatherThanBecoming500s() {
		this.rest.get().uri("/no-such-path").exchange().expectStatus().isNotFound();
		this.rest.put()
			.uri(PATH + "/1")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{}")
			.exchange()
			.expectStatus()
			.isEqualTo(405);
		this.rest.get().uri(PATH + "/not-a-number").exchange().expectStatus().isBadRequest();
		this.rest.get().uri(PATH + "?favourite=bogus").exchange().expectStatus().isBadRequest();
		this.rest.post().uri(PATH).body("x").exchange().expectStatus().isEqualTo(415);
	}

	@Test
	void everyErrorHasTheSameShape() {
		// 404 and 400 differ only in status and detail; a client parses one type either way.
		this.rest.get()
			.uri(PATH + "/999999")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo(404)
			.jsonPath("$.title")
			.exists()
			.jsonPath("$.detail")
			.exists()
			.jsonPath("$.instance")
			.isEqualTo(PATH + "/999999");
	}

	private RestTestClient.ResponseSpec post(String body) {
		return this.rest.post().uri(PATH).contentType(MediaType.APPLICATION_JSON).body(body).exchange();
	}

}
