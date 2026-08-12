# Bookmarks

A small read-it-later service: save a link with a title, tags and notes, then list, search and
favourite them. Java + Spring Boot backend, native Android client.

Take-home task for BirSearch.

| | |
|---|---|
| **Live API** | _to be filled in_ |
| **Android APK** | _to be filled in_ |
| **Decisions & trade-offs** | [NOTES.md](NOTES.md) |

> The API is hosted on a free tier that sleeps when idle. The first request after a quiet
> period takes around 50 seconds while the container wakes up. Subsequent requests are normal.

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

Requires a connected device or emulator. Debug builds point at a local backend; the released
APK points at the deployed one.

## API

```
GET    /api/v1/bookmarks?q=&tag=&favorite=&page=&size=&sort=
POST   /api/v1/bookmarks
GET    /api/v1/bookmarks/{id}
PATCH  /api/v1/bookmarks/{id}
DELETE /api/v1/bookmarks/{id}
```

Search covers title, tags and notes, and results are ranked — a title match outranks a tag
match, which outranks a match in the notes.
