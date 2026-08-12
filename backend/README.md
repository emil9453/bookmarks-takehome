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

## Other commands

```bash
./mvnw test      # unit and slice tests
./mvnw verify    # full build — run this before calling anything done
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
