# Bookmarks

A small read-it-later service: save a link with a title, tags and notes, then list, search and
favourite them. Java + Spring Boot backend, native Android client.

Take-home task for BirSearch.

[![Backend](https://github.com/emil9453/bookmarks-takehome/actions/workflows/backend.yml/badge.svg)](https://github.com/emil9453/bookmarks-takehome/actions/workflows/backend.yml)

| | |
|---|---|
| **Live API** | https://bookmarks.178.104.76.109.sslip.io |
| **API docs** | https://bookmarks.178.104.76.109.sslip.io/swagger-ui.html |
| **Health check** | https://bookmarks.178.104.76.109.sslip.io/actuator/health |
| **Android APK** | [BirBookmarks-1.1.apk](https://github.com/emil9453/bookmarks-takehome/releases/download/v1.1/BirBookmarks-1.1.apk) ([release](https://github.com/emil9453/bookmarks-takehome/releases/tag/v1.1)) |
| **Decisions & trade-offs** | [NOTES.md](NOTES.md) |

> The APK is built from tag `v1.1`, which is `main`. It scopes bookmarks per install
> (`X-Client-Id`, below), so two phones installing it do not share a list.

> Self-hosted on a small VPS: Docker under Coolify, behind Traefik with a Let's Encrypt
> certificate. It does not sleep, so the first request is as fast as the rest — around 250ms.
> It ran on a free tier first; that tier slept after 15 minutes idle and woke in 60 to 120+
> seconds, which is a poor thing to hand a reviewer. [NOTES.md](NOTES.md) has the reasoning.

Quick look:

```bash
curl https://bookmarks.178.104.76.109.sslip.io/api/v1/bookmarks

curl -X POST https://bookmarks.178.104.76.109.sslip.io/api/v1/bookmarks \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://kotlinlang.org","title":"Kotlin docs","tags":["kotlin"],"notes":"coroutines"}'

curl 'https://bookmarks.178.104.76.109.sslip.io/api/v1/bookmarks?q=kotlin'
```

## Layout

```
backend/    Spring Boot API   — see backend/README.md
mobile/     Android app       — see mobile/README.md
NOTES.md    Key decisions, trade-offs, and how AI tooling was used
```

## Running the backend

No database setup, no configuration:

```bash
cd backend
./mvnw spring-boot:run
```

Starts on `http://localhost:8080` against an in-memory database. The deployed instance runs the
same code against Postgres.

## Running the app

```bash
cd mobile
./gradlew installDebug
```

Requires a connected device or emulator. **Both** build types point at the deployed API above —
there is no localhost anywhere in the app, which is what lets a USB-connected phone run it with no
`adb reverse`, no LAN address and no cleartext exception. See [mobile/README.md](mobile/README.md)
for the build details and why the first request of a session is slow.

## API

```
GET    /api/v1/bookmarks?q=&tag=&favourite=&page=&size=&sort=
POST   /api/v1/bookmarks
GET    /api/v1/bookmarks/{id}
PATCH  /api/v1/bookmarks/{id}
DELETE /api/v1/bookmarks/{id}
```

`/api/v1/bookmarks` is canonical, so a breaking change has an obvious home in `/api/v2`.
**`/bookmarks` maps to the same resource**, because that is the path the API was specified with
and a caller who uses it should get the resource rather than a `404`.

Every endpoint is scoped to an **`X-Client-Id`** header. The app generates a UUID on first launch
and sends it on every request, so two phones do not share one list. This is separation, not
authentication: the header is self-asserted, so anyone who copies one reads that collection — the
brief rules out accounts, and this is the smallest thing that keeps two installs apart without
them. A request with no header falls back to a shared collection, which is what the `curl` examples
above and the Swagger page use. Another client's bookmark id answers `404`, not `403`: ids are
sequential, and `403` would confirm the row is there.

Interactive docs, generated from the controllers rather than hand-written, are at
[`/swagger-ui.html`](https://bookmarks.178.104.76.109.sslip.io/swagger-ui.html).

Search covers title, tags and notes, and results are ranked — a title match outranks a tag
match, which outranks a match in the notes, and the scores add up, so a bookmark matching in
two places outranks one matching in a single place. `q`, `tag` and `favourite` combine freely.

`?favorite=` binds as well as `?favourite=`. An unknown query parameter is not an error in
Spring MVC — it is dropped and the request answers `200` with the filter silently ignored, which
is the worst way for a spelling to fail. Request bodies take both spellings too, for the opposite
reason: an unknown *field* is a `400`, so without the alias a create request copied out of the
API description was rejected outright. A genuine typo still fails and still names the field.
Responses and the Android client use the British spelling throughout.

The deployed instance is rate limited at the proxy — 60 requests a minute per source address,
bursting to 20, and a 256KB body cap. Clicking around never reaches it; a script will.
[`backend/README.md`](backend/README.md) has the configuration and the reasoning.

`backend/seed.sh [base-url]` fills an instance with a realistic reading list, arranged so that
one query shows the whole ranking rule at once:

```
$ curl '.../api/v1/bookmarks?q=kotlin'

1. Kotlin coroutines: a deep dive      title + tag  = 5
2. Structured concurrency in practice  tag + notes  = 3   ← ahead of the next on breadth, not score
3. Kotlin Multiplatform overview       title        = 3
4. Why we moved off Java               notes        = 1
```

Errors are RFC 9457 problem details. A validation failure names the field:

```json
{ "status": 400, "title": "Bad Request", "detail": "The request has 2 invalid fields.",
  "errors": { "title": "must not be blank", "url": "must be a valid http or https link" } }
```
