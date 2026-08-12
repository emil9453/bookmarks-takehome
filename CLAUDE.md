# Bookmarks

Read-it-later app: Spring Boot 4 API + native Android client. Take-home for BirSearch.
**Deadline Sunday 16 August 2026** (brief says Monday 17th 10:00; the covering email says
Sunday — targeting Sunday makes the conflict moot). Total budget ~8-10 hours across both halves.

A 30-40 minute walkthrough follows where the reviewers dig into the choices. Every decision
here needs a spoken defence, so the reasoning matters more than the polish.

## Non-negotiable

The brief says, verbatim: *"No auth, no over-engineering. Small and clean beats big and unfinished."*

- **No auth, no user model, no owner column.**
- **One service.** Decomposition was evaluated against a real checklist and rejected. Do not
  reopen it.
- **Do not build for scale.** The scale path is written into NOTES.md, not into the code.
  Full-text search, keyset pagination, caching and replicas are all deliberately absent.
- **No new dependency** for something a few lines of the standard library covers.
- If you find yourself adding an abstraction the ticket did not ask for, stop and re-read the
  ticket.

## Layout

```
backend/    Spring Boot 4 API      — see backend/CLAUDE.md
mobile/     Android app (Kotlin)   — see mobile/CLAUDE.md
NOTES.md    Decisions, trade-offs, AI use          ← required deliverable
README.md   What this is, live URL, APK link
```

## Work is tracked in Linear

Project **Bookmarks Take-Home**, team **BOO**. Issues carry the acceptance criteria — the
"Done when" list *is* the definition of done, all of it. Labels split the streams: `Backend`,
`Mobile`, `Shared`.

Backend and mobile run as separate sessions. Work only your own label.

## The loop

Repeat until no Todo issues remain for this session's label:

1. **Pick** the highest-priority unblocked `Todo` issue for your label. Set it `In Progress`.
2. **Read** it fully. Treat every "Done when" bullet as a hard requirement.
3. **Build** the smallest thing that satisfies them.
4. **Review** with a cold context — `/review` runs the matching stack reviewer agent
   (`spring-reviewer` / `android-reviewer`); `/code-review` is the generic built-in pass.
   A fresh reader catches what the author cannot.
5. **Fix** what the review found. Re-review only if the fix was substantial.
6. **Test.** Backend: `./mvnw test`. Mobile: `./gradlew testDebugUnitTest` plus a real install
   on the device.
7. **Commit**, with the issue id in the message. **Stage only your own subtree** —
   `git add backend/` or `git add mobile/`, never `git add -A`. The two sessions may be
   running at once, and a blanket stage sweeps the other one's half-finished files into your
   commit.
8. **Close** the issue. Comment on it if a decision changed along the way.
9. Next.

## Keep a running NOTES scratch

NOTES.md must name **one place an AI tool produced wrong Java or Kotlin and how you caught it**.
That is a required deliverable and it cannot be reconstructed from memory on Sunday night.

The moment a tool hands you code that does not compile, does not run, or is quietly wrong,
append a line to `NOTES-scratch.md` — what it wrote, what was wrong, how it surfaced. Two
sentences. Do it immediately, not later.

## Skills

Installed under `~/.claude/skills/`. They carry framework conventions and known traps; they load
themselves when relevant. This file and the two below carry the decisions specific to *this*
project. When they disagree, this file wins — the deviations are deliberate and are listed where
they occur.
