# Notes

A read-it-later service: Spring Boot 4 API plus a native Android client. Built over two days
against a brief whose governing line was *"no auth, no over-engineering — small and clean beats
big and unfinished."* Most of what follows is therefore about what was left out.

## The short version

The brief asked for half a page. This is it; everything below is the supporting detail, there
because a 30-40 minute walkthrough will go looking for it.

**Decisions.** One service — one aggregate, one deployment, nothing with a different scaling
profile, so a split would buy a network hop and nothing else. Errors are RFC 9457 problem
details rather than a custom envelope, because the framework already produces them and a
bespoke wrapper would have meant a generic type around every model on the phone. Flyway owns
the schema, not `ddl-auto`, so the migration that ran against H2 is the one that ran against
production. Search ranking is a weighted sum — title 3, tag 2, notes 1, added together, scored
in the database so page one holds the best results overall. On the phone: no DI framework, no
paging library, no local database, each one a deliberate omission with a stated trigger for
when it would earn its place.

**Trade-offs.** `LIKE '%q%'` cannot use an index; it is honest at 35 rows and wrong at 35,000.
Offset pagination walks the rows it discards. Both are the right call at this size and both are
the first things to change.

**With more time:** Postgres full-text search, keyset pagination, and two Espresso tests over
the add-then-return path — every bug that actually reached the device was a Compose-lifecycle
bug that unit tests structurally cannot see.

**AI tools.** Claude Code (Opus 5) for both halves, with Linear over MCP holding the acceptance
criteria. No design AI: the target was an existing product, so the palette, typeface and icon
geometry were measured out of birbank.az rather than generated.

**Where a tool got it wrong.** A generated ViewModel carried the comment *"Tracked so a refresh
cannot race a load-more that is already in flight"* over code that tracked no such thing —
`firstPageJob` only ever held first-page jobs. Pull to refresh mid-load-more, and the same page
appended twice; `LazyColumn` keys on id, so duplicate ids threw and killed the app. It compiled
and the tests passed. A cold-context review caught it, and removing each part of the fix in turn
showed which one was actually load-bearing — the cursor-equality check, not the job cancel.

---

## Key decisions and trade-offs

**One service, not two.** Splitting was considered properly and rejected: there is one aggregate,
one team, one deployment, and nothing with a different scaling profile. A second service would
have bought a network hop and a distributed transaction in exchange for nothing. The trigger for
revisiting is a second bounded context with genuinely different write patterns — not traffic.

**RFC 9457 problem details rather than a custom envelope.** Spring already produces
`ProblemDetail`, so a bespoke `{success, data, error}` wrapper would have meant writing code to
replace something the framework does correctly, and clients would have had to learn a private
format. Errors carry `type`, `title`, `status`, `detail`, and validation failures add an `errors`
object naming the offending fields.

**H2 locally, Postgres in production, one schema.** The API starts with `./mvnw spring-boot:run`
and no database to install, which is what the brief asked for. Flyway owns the schema rather than
`ddl-auto`, so the migration that runs against H2 is the same one that ran against production —
`ddl-auto` in production is how schemas drift silently.

**Moved off the free hosting tier, and the move cost no code.** The API first ran on a free tier
that slept after 15 minutes idle. Its wake was measured at 62.6s once and at over 120s two days
later, which is the first thing a reviewer would have met. It now runs on a small VPS under
Coolify, behind Traefik with a Let's Encrypt certificate: ~250ms, no sleep. What made the move a
config change rather than a project was that the deploy contract is a Dockerfile plus
`DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` — no platform SDK, no vendor client, no
build-time coupling to a host. Not one line of Java changed. Deploys are a CLI call rather than a
push hook, which is a fair trade for owning the uptime.

**Search ranking is a weighted sum, in two keys.** Title match scores 3, tag 2, notes 1, and they
are summed, so a bookmark matching title *and* tag outranks one matching title alone. The second
key is how many fields matched at all, because the weights collide: a title-only hit scores 3, and
so does tag-plus-notes. Without the tiebreak those two are ordered arbitrarily, which looks like a
bug precisely when someone is judging the ranking. `LIKE` is honest for this dataset size; the
upgrade path is Postgres full-text, noted below rather than built.

**What the Android app deliberately does not have.** No dependency-injection framework — three
objects need constructing, and a hand-written `Network` holder is the seam; Hilt earns its place
around five injectables or the first module split. No paging library — the four states the brief
names are four types in a sealed interface, which makes them literal and demonstrable, where
Paging 3 buries them inside `LoadState`. No local database and no offline mode — not asked for,
and it brings sync conflicts with it. Each is a sentence I can defend, which is the point.

**Branding.** The app is BirBookmarks and wears the Birbank design language — their red
(`#EC3342`), Onest, their ribbon mark, white cards on a grey page. The argument it makes is that
the client is separable from presentation: the skin changed completely — palette, type,
iconography, navigation shell — without touching a request, a model or a ViewModel. It was
sequenced after every feature was finished, so nothing functional was traded for it. Their bottom
bar has five tabs because their app has five things; this one has two, because padding it out
would have put tabs in that lead nowhere.

## What I'd do with more time

- **Postgres full-text search** with a `tsvector` column and a GIN index, replacing `LIKE '%q%'`.
  The current query cannot use an index for a leading wildcard, so it is a sequential scan that is
  fine at 35 rows and wrong at 35,000.
- **Keyset pagination** instead of offset. `OFFSET 10000` makes the database walk 10,000 rows to
  discard them; a `(score, id)` cursor does not.
- **Instrumented UI tests.** The unit tests cover the ViewModels, but every bug that actually
  reached the device this weekend was a Compose-lifecycle bug that unit tests structurally cannot
  see. Two Espresso tests over the add-then-return path would have caught both.
- **A proper empty-state illustration and an "undo" on delete.** Deletion is currently immediate
  and irreversible, which is the wrong default for a destructive action on a list.

## How it scales

This is a data-partitioning problem, not a distributed-systems one. Millions of users each holding
a few hundred bookmarks means every query touches a few hundred rows — provided queries are scoped
to an owner and indexed for it. It never becomes a millions-of-rows scan, which is exactly why
splitting into services would help with none of it.

**The owner column is the missing piece, and its absence is the decision.** Adding it later is
cheap: one column, backfill to a single anonymous owner, make the indexes composites led by it,
add one condition per query. No API change and no data movement. It is not in the code because the
brief says no auth — the reasoning is the deliverable, not the column.

After that, in order: full-text search instead of `LIKE`, keyset pagination instead of offset, a
cache in front of the hot list query, then read replicas. Only the pagination change touches the
API contract, which makes it the expensive one to defer; it was deferred anyway because the brief
asks for pagination by name and per-user page depth stays shallow.

**Worth separating two reasons a search cluster ever gets added**, at a company called BirSearch:
typo tolerance and multi-language stemming are *capability*, not load. Reaching for Elasticsearch
because queries got slow is a different decision from reaching for it because you need fuzzy
matching in three languages, and only the second one is about search. Neither is in the code.

## AI tools

**Claude Code (Opus 5) for both halves**, with Linear over MCP for the ticket flow — the
acceptance criteria lived in the issues and each ticket was closed against its own "done when"
list rather than a general sense of being finished. It genuinely accelerated the parts where I had
no muscle memory: Compose layout, Kotlin coroutine and `Flow` idiom, and the Boot 4 API surface,
which has moved enough since Boot 3 that recall — mine and the model's — is unreliable.

**No design AI tool.** The brief offers v0 / Figma AI / Stitch, and I used none. The design target
was an existing product, so generating one would have been the wrong move: the palette came out of
birbank.az's own CSS (`#EC3342`, `#25282B`, `#9496AC`), the typeface from their `@font-face`
declaration (Onest, Open Font Licence, so it ships inside the APK), and the icon geometry from
measuring their 152px app icon — a true parallelogram, vertical sides, two parallel 45° cuts —
rather than eyeballing it. Measuring took less time than prompting would have, and it is defensible
value by value.

**Where it cost time rather than saved it** is the section below. The pattern worth naming: the
model was reliable on things with one right answer and unreliable on things whose right answer
changed recently or depends on a lifecycle it cannot see.

### Where a tool got it wrong

**A comment that asserted a guard the code did not have — over a crash.** The generated
`BookmarkListViewModel` kept the paging cursor in a `var nextPage` and tracked a single `Job`
called `firstPageJob`, above the comment *"Tracked so a refresh cannot race a load-more that is
already in flight."* That sentence was simply false. `firstPageJob` only ever held first-page
jobs, so cancelling it did nothing to an in-flight `loadMore`, whose only real guard was a
`loadingMore` flag that the refresh success path overwrote.

The consequence was not a wrong list — it was a crash. Pull to refresh while page 2 is loading,
scroll back down, and the same page appends twice; because `LazyColumn` keys on `it.id`, duplicate
ids make `SaveableStateProvider` throw `IllegalArgumentException("Key … was used multiple times")`
and the app dies on the frame the second append lands. It compiled, it ran, and the ~50-second
cold start of the hosting tier in use at the time made the race window wide enough to hit by hand.

**How it surfaced:** not the compiler and not a test — a cold-context review pass reading the code
without having written it. The fix moved the cursor into the `Data` state so it cannot desync from
the list it describes, appending only when `latest.nextPage == page`.

**The part worth keeping:** when I checked which change was actually load-bearing by removing them
one at a time, cancelling the job turned out **not** to be — the test still passed without it. Only
removing the cursor-equality check reproduced the duplicate ids. "I fixed it" was worth verifying
by breaking it again on purpose.

Three others in the same family:

- **Jackson 3 flipped a default.** A primitive `boolean favourite` on a request record made every
  POST that merely *omitted* the field return `400 "Failed to read request"`. Boot 4 ships Jackson
  3, which turns `FAIL_ON_NULL_FOR_PRIMITIVES` on where Jackson 2 had it off — an absent boolean
  went from "defaults to false" to "rejects the request" purely by upgrading. Only visible with a
  field missing, which is exactly what the Android client sends when the user does not tick
  favourite: a first-run-on-device bug, not a compile error.
- **An exception handler that compiled, registered, and never ran** — and whose obvious fix
  (`@Order(HIGHEST_PRECEDENCE)`) was worse than the bug, turning six framework-handled cases into
  `500`s while all 30 tests that existed at the time stayed green, because none of them requested a
  URL that does not exist. The regression has a test now.
- **`remember` used for state that had to outlive the composable.** A "skip the first resume" flag
  lived in `remember`, so Navigation Compose disposed it on every departure and the list never
  reloaded after adding a bookmark. No crash, no warning, no failing test — caught only by saving a
  real bookmark on the device and watching the server reach 34 rows while the screen showed the old
  one.

The through-line: three of the four compiled cleanly and passed the tests that existed. What caught
them was a cold reader, a real device, and a test that asserted the message rather than the status
code.

The suite stands at 36 backend tests and 40 on the mobile side. It is not there for coverage — it
is there because each of those numbers grew by one on the day something above got through. The
most recent pair came from a review pass over the finished project: the brief spells the filter
`favorite` and this API spells it `favourite`, and an unrecognised query parameter is not an
error in Spring MVC — `?favorite=true` was answering `200` with every bookmark in it and the
filter quietly dropped. A wrong request body was already caught loudly; the query string had no
such backstop. Both spellings now bind, and the test fails on the American one if the alias is
removed.
