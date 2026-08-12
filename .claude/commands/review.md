---
description: Cold-context review of the current changes with the matching stack reviewer agent
---

Review the current uncommitted changes using the project's reviewer agent.

Pick the agent from `$ARGUMENTS` if given (`backend`/`spring` → `spring-reviewer`,
`mobile`/`android` → `android-reviewer`). Otherwise decide from `git status`: changes under
`backend/` go to `spring-reviewer`, changes under `mobile/` go to `android-reviewer`, and
changes to both mean running both.

Spawn the agent with `run_in_background: false` — there is nothing useful to do while waiting
for a review of the code you just wrote.

Tell it which Linear issue this work belongs to, so it can check the "Done when" list. Find the
issue from the current branch name or the most recent commit if it is not obvious.

When the agent reports back, relay its findings verbatim rather than summarising them, then
fix what is real. Push back on anything that recommends adding something the project
deliberately skipped — the reviewers are told about those, but a wrong finding is still
possible, and the deviations are listed in `CLAUDE.md`.
