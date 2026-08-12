# Backend — Spring Boot 4

## Stack

Spring Boot 4.x, Spring Framework 7, Java 21, Maven wrapper. Jakarta EE 11, Jackson 3, Boot 4
modular starters. H2 locally, Postgres in production.

## Commands

```bash
./mvnw spring-boot:run          # must work from a clean clone with zero setup
./mvnw test                     # unit + slice tests
./mvnw -Dtest=ClassName test    # single test
./mvnw verify                   # before calling a task done
```

## Decisions specific to this project

These override anything the skills say. Each one is deliberate and each one will be asked about.

**Errors: RFC 9457 `ProblemDetail`. No success envelope.**
Success responses are the plain resource. Errors use Spring's built-in `ProblemDetail`.
This deliberately contradicts the `rest-api-conventions` skill, which wants
`{success, data, error, timestamp}`. The envelope would force a generic wrapper type through
every Kotlin model on the phone; ProblemDetail is a published standard and ships in the
framework. Less code at both ends.

**Package by feature, not by layer.**
`com.emil.bookmarks.bookmark` holds its own controller, service, repository and DTOs.
`common/` for the exception handler, `config/` for wiring. The layering rules still apply
in full — controller → service → repository, DTOs at the boundary, `@Transactional` on the
service. One feature package today; a second aggregate would get its own.

**Two database profiles.**
Default is H2, because the brief requires a one-command start with no setup. The `prod`
profile is Postgres, because H2 in a container loses everything on restart. Flyway runs on
both, so **migration SQL must be portable** — plain types only, no Postgres-only syntax.
`ddl-auto: validate`, never `update`.

**Search ranking: weighted sum, not first match.**
`title 3 + tag 2 + notes 1`, added together, so a bookmark matching title *and* tag outranks
one matching only the title. This is the highest-signal piece of the whole task — the company
is BirSearch.

**The sort must end with a unique key.**
`ORDER BY score DESC, created_at DESC, id`. Scores are small integers so ties are constant,
and each page is a separate query. Without the final `id` the same row can appear on two
pages, or none. This is a correctness bug at fifty rows, not a scale concern.

**Indexes are part of the migration.**
One for the default newest-first list order, one for tag filtering. Two lines, free now.

## Traps

The installed Boot 4 skills carry the full list. The ones that will actually bite:

- AI writes `spring-boot-starter-web`. Boot 4 renamed it `spring-boot-starter-webmvc`
  (`-webmvc-test` for MockMvc).
- AI writes Jackson 2 (`ObjectMapper` beans, `@JsonComponent`). Boot 4 is Jackson 3:
  `JsonMapper`, `@JacksonComponent`.
- AI writes `@SpringBootTest` and expects MockMvc. Boot 4 no longer auto-provides it — add
  `@AutoConfigureMockMvc`.
- Fetch-joining tags to avoid the per-row query silently breaks pagination — it pages in
  memory. Use batched loading instead.
- Optional filter parameters that work on H2 can fail on Postgres, which is stricter about
  untyped nulls. Verify against the deployed database, not just locally.

Each of these is a legitimate NOTES.md entry if it costs you time. Log it when it happens.

## Done means

`./mvnw verify` passes, and every "Done when" bullet on the Linear issue is satisfied.
