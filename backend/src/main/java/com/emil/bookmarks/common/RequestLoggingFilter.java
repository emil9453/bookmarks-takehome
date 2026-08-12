package com.emil.bookmarks.common;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logs one line per request and tags every log line made while handling it with a trace id.
 *
 * <p>A full tracing stack (Micrometer Tracing plus a Brave or OpenTelemetry bridge) would give
 * the same trace id here for a lot more moving parts, and there is no second service to
 * propagate it to.
 */
@Component
class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	private static final String TRACE_ID = "traceId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		// Generated, never read from a request header: a caller-supplied value would land in
		// the log file unescaped, and newlines in it would forge log lines.
		String traceId = UUID.randomUUID().toString().substring(0, 8);
		MDC.put(TRACE_ID, traceId);
		response.setHeader("X-Trace-Id", traceId);

		long startedAt = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			log.info("{} {} -> {} ({} ms)", request.getMethod(), path(request), response.getStatus(),
					(System.nanoTime() - startedAt) / 1_000_000);
			MDC.remove(TRACE_ID);
		}
	}

	/**
	 * Path with the query string kept — for a search request the {@code ?q=} is the part worth
	 * seeing. Both come back still URL-encoded, so a newline a caller tried to smuggle in stays
	 * {@code %0A} and cannot forge a log line.
	 */
	private static String path(HttpServletRequest request) {
		String query = request.getQueryString();
		return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// The host pings the health endpoint constantly; keep it out of the log.
		return request.getRequestURI().startsWith("/actuator");
	}

}
