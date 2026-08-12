package com.emil.bookmarks.bookmark;

import java.util.Set;
import java.util.TreeSet;

/**
 * Thrown for a sort key the API does not offer. Without this, an unknown key reaches Spring
 * Data and comes back as a 500 — a client typo reported as a server fault.
 */
public class InvalidSortException extends RuntimeException {

	public InvalidSortException(String property, Set<String> allowed) {
		super("Cannot sort by '" + property + "'. Allowed: " + new TreeSet<>(allowed));
	}

}
