# Bookmarks

A small read-it-later service: save a link with a title, tags and notes, then list, search and
favourite them. Java + Spring Boot backend, native Android client.

Take-home task for BirSearch.

[![Backend](https://github.com/emil9453/bookmarks-takehome/actions/workflows/backend.yml/badge.svg)](https://github.com/emil9453/bookmarks-takehome/actions/workflows/backend.yml)

| | |
|---|---|
| **Live API** | https://bookmarks-api-4i5h.onrender.com |
| **API docs** | https://bookmarks-api-4i5h.onrender.com/swagger-ui.html |
| **Health check** | https://bookmarks-api-4i5h.onrender.com/actuator/health |
| **Android APK** | [BirBookmarks-1.0.apk](https://github.com/emil9453/bookmarks-takehome/releases/download/v1.0/BirBookmarks-1.0.apk) ([release](https://github.com/emil9453/bookmarks-takehome/releases/tag/v1.0)) |
| **Decisions & trade-offs** | [NOTES.md](NOTES.md) |

> The API is hosted on a free tier that sleeps when idle. The first request after a quiet
> period waits for the container to wake — measured between 60 and 120+ seconds, depending on
> how long it has been down. Subsequent requests are normal. Nothing is broken — it is worth
> hitting the health check once and waiting before judging anything else, including the app.

Quick look:

```bash
curl https://bookmarks-api-4i5h.onrender.com/api/v1/bookmarks

curl -X POST https://bookmarks-api-4i5h.onrender.com/api/v1/bookmarks \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://kotlinlang.org","title":"Kotlin docs","tags":["kotlin"],"notes":"coroutines"}'

curl 'https://bookmarks-api-4i5h.onrender.com/api/v1/bookmarks?q=kotlin'
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

Interactive docs, generated from the controllers rather than hand-written, are at
[`/swagger-ui.html`](https://bookmarks-api-4i5h.onrender.com/swagger-ui.html).

Search covers title, tags and notes, and results are ranked — a title match outranks a tag
match, which outranks a match in the notes, and the scores add up, so a bookmark matching in
two places outranks one matching in a single place. `q`, `tag` and `favourite` combine freely.

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
