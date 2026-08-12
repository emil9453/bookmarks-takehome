package com.emil.bookmarks;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Guards the headline requirement: a clean clone starts with one command and reports healthy.
 * Boots the real server against the real configuration, so a typo in application.yml or a
 * broken migration fails the build instead of only failing the demo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class BookmarksApplicationTests {

	@Autowired
	private RestTestClient rest;

	@Test
	void startsWithNoConfigurationAndReportsHealthy() {
		rest.get()
			.uri("/actuator/health")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("UP");
	}

}
