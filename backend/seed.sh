#!/usr/bin/env bash
# Fills an instance with a realistic reading list.
#
#   ./seed.sh                                        # local
#   ./seed.sh https://bookmarks-api-4i5h.onrender.com # deployed
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

echo
echo "Try:  curl '$BASE?q=kotlin' | jq '.content[].title'"
