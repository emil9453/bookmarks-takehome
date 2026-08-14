# Notes

A read-it-later service: Spring Boot 4 API plus a native Android client, built against a brief
whose governing line was *"no auth, no over-engineering — small and clean beats big and
unfinished."* Most of what follows is therefore about what was left out.

## Key decisions and trade-offs

**One service.** One aggregate, one deployment, nothing with a different scaling profile — a split
would buy a network hop and nothing else. The trigger to revisit is a second bounded context with
genuinely different write patterns, not traffic.

**RFC 9457 problem details, not a custom envelope.** Spring already produces `ProblemDetail`, and a
`{success, data, error}` wrapper would have forced a generic type around every model on the phone.
Validation failures add an `errors` object keyed by field name, which is what lets the app put each
message under the right input box.

**Flyway owns the schema, not `ddl-auto`.** The migration that ran against H2 is the one that ran
against Postgres, and `ddl-auto: validate` stops the app if entities and migrations drift apart.

**Search ranking is a weighted sum** — title 3, tag 2, notes 1, added together rather than stopping
at the first field that hits, with a second key on how many fields matched because the weights
collide. Scored in the database, so page one holds the best results overall rather than the best of
whichever rows the page window caught.

**The sort ends with a unique key.** `?sort=title` orders by a column full of ties and each page is
a separate query, so without a final `id` a bookmark lands on two pages or on none — a correctness
bug at fifty rows, not a scale concern.

**Both spellings bind, and both base paths resolve.** An unknown query parameter is not an error in
Spring MVC — `?favorite=true` answered `200` with the filter silently dropped, which is the worst
way for a spelling to fail. `/bookmarks` maps alongside `/api/v1/bookmarks` for the same reason.

**On the phone:** no DI framework, no paging library, no local database. Three objects need
constructing; the four states the brief names are four types in a sealed interface, where Paging 3
would bury them inside `LoadState`.

**What I accepted.** `LIKE '%q%'` cannot use an index — honest at 35 rows, wrong at 35,000. Offset
pagination walks the rows it discards. Both are right at this size and both are the first to change.

## What I'd do with more time

Postgres full-text search with a `tsvector` column and a GIN index; keyset pagination instead of
offset; two Espresso tests over the add-then-return path, because every bug that actually reached
the device was a Compose-lifecycle bug that unit tests structurally cannot see; and an undo on
delete, which is currently immediate and irreversible.

## AI tools

Claude Code (Opus 5) for both halves, with Linear over MCP holding the acceptance criteria so each
ticket closed against its own "done when" list. It accelerated the parts where I had no muscle
memory: Compose layout, Kotlin `Flow` idiom, and the Boot 4 API surface, which has moved enough
since Boot 3 that recall — mine and the model's — is unreliable. No design AI: the target was an
existing product, so the palette, typeface and icon geometry were measured out of birbank.az.

## Where a tool got it wrong

**Jackson 3 flipped a default.** The generated create-request record declared `favourite` as a
primitive `boolean`. Boot 4 ships Jackson 3, which turns `FAIL_ON_NULL_FOR_PRIMITIVES` **on** where
Jackson 2 had it off, so every POST that merely *omitted* the field came back
`400 "Failed to read request"` — which is exactly what the Android client sends when the user does
not tick favourite. It compiled, and it only misbehaves with the field absent, so nothing in the
suite saw it; it surfaced on the first real save from the device. The fix is a boxed `Boolean`.

Three others in the same family: an exception handler that registered and never ran, whose obvious
fix (`@Order(HIGHEST_PRECEDENCE)`) turned six framework-handled cases into `500`s; `remember` used
for a flag that had to outlive the composable; and a paging cursor that could desync from the list
it described until `LazyColumn`'s duplicate keys killed the app.

All four compiled. What caught them was a cold reader, a real device, and a test that asserted the
message rather than the status.
