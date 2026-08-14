# Bookmarks API

Spring Boot 4 · Java 21 · Maven.

## Run it

```bash
cd backend
./mvnw spring-boot:run
```

No database to install, no configuration to write. The default profile uses an in-memory H2
database, so a clean clone starts on the first command. Everything is gone again when the
process stops — that is the point of the default profile, not an oversight.

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Interactive API docs at `http://localhost:8080/swagger-ui.html`, generated from the controllers
so they cannot drift away from the code.

```bash
./seed.sh        # fill it with a realistic reading list to click around
```

## Other commands

```bash
./mvnw test      # unit and slice tests
./mvnw verify    # full build — run this before calling anything done, and what CI runs
```

## How the code is organised

Packaged by feature, not by layer:

```
com.emil.bookmarks
├── bookmark/     the one feature: controller, service, repository, entity, DTOs
└── common/       cross-cutting bits — request logging, and later the error handler
```

Inside the feature package the layers are still strictly separated, and the dependencies only
ever point one way:

| Layer          | Type            | Job                                               |
|----------------|-----------------|---------------------------------------------------|
| HTTP           | `…Controller`   | Parse and validate the request, map to a DTO. No business rules. |
| Business logic | `…Service`      | The rules, and the transaction boundary.          |
| Data access    | `…Repository`   | Spring Data JPA. No HTTP types, ever.             |

A controller never touches a repository, and an entity never leaves the service layer — the
API speaks DTOs, so the database schema is free to change without breaking clients.

A second aggregate would get its own package next to `bookmark/`, with the same internal split.

## Logging

Every request logs one line, and every log line written while handling it carries a short trace
id. The same id comes back to the caller in the `X-Trace-Id` response header, so a report of
"it failed at 14:32" can be traced to the exact request.

Micrometer Tracing plus a Brave or OpenTelemetry bridge would produce the same id here at the
cost of three more dependencies, and there is no second service to propagate a trace to.

## Configuration

No datasource is configured. H2 is on the classpath and no url is set, so Boot auto-configures
an in-memory database — which is what makes the one-command start work. Leaving it unset also
means the deployed profile fails loudly if its database url is missing, rather than quietly
falling back to an in-memory database and reporting itself healthy.

Flyway owns the schema and Hibernate runs with `ddl-auto: validate`, so the app refuses to
start if the entities and the migrations disagree.

## Rate limit and body cap

The brief says no auth, so the deployed API is writable by anyone who finds it. That is
accepted — but "anyone can write" should not also mean "anyone can wipe it in a loop". The
limit lives in the reverse proxy rather than in the application: Traefik already terminates
TLS and sees the client address, and a bucket in Java would mean a dependency, a filter and
an in-memory map that a second instance would not share.

Set as custom Docker labels on the Coolify application (Configuration → Advanced), where
`<uuid>` is the router name already listed there:

```
traefik.http.middlewares.bookmarks-rl.ratelimit.average=60
traefik.http.middlewares.bookmarks-rl.ratelimit.period=1m
traefik.http.middlewares.bookmarks-rl.ratelimit.burst=20
traefik.http.middlewares.bookmarks-buf.buffering.maxRequestBodyBytes=262144
traefik.http.routers.https-0-<uuid>.middlewares=bookmarks-rl@docker,bookmarks-buf@docker
```

60 requests a minute per source address, bursting to 20. A person clicking around never
reaches it. The 256KB body cap is the same argument: without it a multi-megabyte POST is
read into memory in full before validation rejects it on a field length, and the proxy can
refuse it without the application ever allocating.

```bash
for i in $(seq 1 90); do curl -s -o /dev/null -w "%{http_code}\n" \
  https://bookmarks.178.104.76.109.sslip.io/api/v1/bookmarks; done | sort | uniq -c
```

The tail of that run should be `429`.
