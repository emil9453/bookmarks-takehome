#!/usr/bin/env bash
# Fills an instance with a realistic reading list.
#
#   ./seed.sh                                                 # local
#   ./seed.sh https://bookmarks.178.104.76.109.sslip.io       # deployed
#
# Additive, not idempotent — running it twice gives you two of everything.
#
# The four kotlin entries are chosen so `?q=kotlin` demonstrates the whole ranking rule in a
# single query, without needing anything explained first:
#
#   1. title + tag        = 3 + 2 = 5
#   2. tag + notes        = 2 + 1 = 3   ← above the next one on breadth, not score
#   3. title only         = 3
#   4. notes only         = 1
#
# The rest are there so the unfiltered list looks like something a person actually saved.

set -euo pipefail
BASE="${1:-http://localhost:8080}/api/v1/bookmarks"

save() {
  curl -sS -o /dev/null -w "  %{http_code}  $2\n" \
    -X POST "$BASE" -H 'Content-Type: application/json' \
    -d "$1"
}

echo "Seeding $BASE"

save '{"url":"https://kotlinlang.org/docs/coroutines-guide.html","title":"Kotlin coroutines: a deep dive","tags":["kotlin","android","concurrency"],"notes":"The cancellation section is the part I keep coming back to."}' "kotlin coroutines (title+tag)"
save '{"url":"https://openjdk.org/jeps/453","title":"Structured concurrency in practice","tags":["kotlin","jvm"],"notes":"Java flavoured, but the kotlin examples are the clearest part of it."}' "structured concurrency (tag+notes)"
save '{"url":"https://kotlinlang.org/docs/multiplatform.html","title":"Kotlin Multiplatform overview","tags":["mobile"],"notes":"Worth a look before committing to two codebases."}' "multiplatform (title only)"
save '{"url":"https://www.infoq.com/articles/jvm-language-migration/","title":"Why we moved off Java","tags":["jvm","architecture"],"notes":"Half of it is really about kotlin adoption at a large company."}' "moved off java (notes only)"

save '{"url":"https://use-the-index-luke.com/","title":"Use The Index, Luke","tags":["postgres","performance"],"notes":"The chapter on composite index column order is the one that finally made it click.","favourite":true}' "index guide"
save '{"url":"https://developer.android.com/jetpack/compose/mental-model","title":"Compose mental model","tags":["android","compose"],"notes":"Recomposition is not the thing I assumed it was."}' "compose"
save '{"url":"https://martinfowler.com/bliki/MonolithFirst.html","title":"Monolith First","tags":["architecture"],"notes":"The argument for not splitting a service before you know where the seams are.","favourite":true}' "monolith first"
save '{"url":"https://www.rfc-editor.org/rfc/rfc9457.html","title":"RFC 9457: Problem Details for HTTP APIs","tags":["api","standards"],"notes":"The error format this project uses instead of a custom envelope."}' "rfc 9457"

# Enough rows to push the list past one page. The client asks for 20, so a reviewer scrolling to
# the bottom is what proves the load-more path exists — with eight rows it never runs.
#
# None of these say "kotlin" anywhere, deliberately: the four entries above are the ranking
# demonstration, and a fifth match would reorder the results the README prints.

save '{"url":"https://www.postgresql.org/docs/current/textsearch.html","title":"Postgres full-text search","tags":["postgres","search"],"notes":"Where the LIKE query in this project would go next."}' "pg fts"
save '{"url":"https://use-the-index-luke.com/no-offset","title":"We need tool support for keyset pagination","tags":["postgres","performance"],"notes":"The argument against OFFSET, with the numbers."}' "keyset"
save '{"url":"https://martinfowler.com/articles/microservice-trade-offs.html","title":"Microservice trade-offs","tags":["architecture"],"notes":"The costs column is the one people skip."}' "micro tradeoffs"
save '{"url":"https://12factor.net/","title":"The Twelve-Factor App","tags":["architecture","ops"],"notes":"Config in the environment is the one that mattered for this deploy."}' "12factor"
save '{"url":"https://httpwg.org/specs/rfc9110.html","title":"HTTP Semantics (RFC 9110)","tags":["api","standards"],"notes":"The PATCH and status code sections settle most API arguments."}' "rfc 9110"
save '{"url":"https://www.rfc-editor.org/rfc/rfc6902","title":"JSON Patch","tags":["api","standards"],"notes":"Considered and rejected: merge semantics were enough here."}' "json patch"
save '{"url":"https://docs.spring.io/spring-boot/index.html","title":"Spring Boot reference","tags":["spring","jvm"],"notes":"The configuration property reference is worth bookmarking on its own."}' "boot ref"
save '{"url":"https://spring.io/blog/2024/03/12/spring-boot-3-vs-4","title":"What changed in Spring Boot 4","tags":["spring","jvm"],"notes":"Jackson 3 and the renamed starters are the two that cost real time."}' "boot 4"
save '{"url":"https://flywaydb.org/documentation/concepts/migrations","title":"Flyway migrations","tags":["postgres","ops"],"notes":"Versioned over repeatable for schema, every time."}' "flyway"
save '{"url":"https://developer.android.com/develop/ui/compose/state","title":"State and Jetpack Compose","tags":["android","compose"],"notes":"remember versus rememberSaveable is the distinction that bites."}' "compose state"
save '{"url":"https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate","title":"Saved state module for ViewModel","tags":["android"],"notes":"A ViewModel survives rotation but not process death. Different problem, different tool."}' "savedstate"
save '{"url":"https://developer.android.com/topic/architecture/ui-layer","title":"The UI layer","tags":["android","architecture"],"notes":"Where the sealed UiState in this project comes from."}' "ui layer"
save '{"url":"https://developer.android.com/guide/navigation/design","title":"Navigation design","tags":["android","compose"],"notes":"Type-safe routes landed and they are worth the migration."}' "navigation"
save '{"url":"https://www.w3.org/WAI/WCAG22/quickref/","title":"WCAG 2.2 quick reference","tags":["accessibility"],"notes":"Contrast minimums for body text are 4.5 to 1, not 3."}' "wcag"
save '{"url":"https://m3.material.io/foundations/accessible-design/patterns","title":"Material accessibility patterns","tags":["accessibility","compose"],"notes":"Touch target minimums and why fixed heights break at large font sizes."}' "m3 a11y"
save '{"url":"https://testing.googleblog.com/2015/04/just-say-no-to-more-end-to-end-tests.html","title":"Just say no to more end-to-end tests","tags":["testing"],"notes":"The shape of the pyramid, argued properly."}' "e2e tests"
save '{"url":"https://martinfowler.com/bliki/TestPyramid.html","title":"The Test Pyramid","tags":["testing","architecture"],"notes":"Short, and still the clearest statement of it."}' "test pyramid"
save '{"url":"https://docs.docker.com/build/building/multi-stage/","title":"Multi-stage Docker builds","tags":["ops","docker"],"notes":"How the image here stays small without a separate build script."}' "multistage"
save '{"url":"https://doc.traefik.io/traefik/middlewares/http/ratelimit/","title":"Traefik rate limiting","tags":["ops","security"],"notes":"Average, burst and period — the three that actually matter."}' "traefik rl"
save '{"url":"https://letsencrypt.org/how-it-works/","title":"How Let'"'"'s Encrypt works","tags":["ops","security"],"notes":"Worth understanding before trusting an automatic certificate."}' "letsencrypt"
save '{"url":"https://owasp.org/www-project-api-security/","title":"OWASP API Security Top 10","tags":["security","api"],"notes":"Most of it assumes auth, which this deliberately does not have."}' "owasp api"
save '{"url":"https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html","title":"Input validation cheat sheet","tags":["security"],"notes":"Validate at the boundary, on the server, always."}' "input validation"
save '{"url":"https://www.sqlite.org/whentouse.html","title":"When to use SQLite","tags":["database"],"notes":"A good reminder that the boring option is often right."}' "sqlite"
save '{"url":"https://brooker.co.za/blog/2021/01/22/cloud.html","title":"Cloud services are not magic","tags":["architecture","ops"],"notes":"The section on managed does not mean maintenance-free."}' "cloud"

echo
echo "Try:  curl '$BASE?q=kotlin' | jq '.content[].title'"
