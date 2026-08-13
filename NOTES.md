# Notes

<!--
Skeleton only — fill each section in as the work happens, not at the end.
The AI section in particular cannot be reconstructed from memory on the last evening;
keep NOTES-scratch.md open and append to it the moment something goes wrong.
-->

## Key decisions and trade-offs

_To fill in. Already settled and worth covering:_

- _Why this stayed a single service rather than being split._
- _Error format: RFC 9457 problem details rather than a custom response envelope._
- _In-memory database locally so it starts with one command; Postgres in production._
- _Search ranking: weighted and summed, so a title-and-tag match outranks title alone._
- _What was left out of the Android app on purpose, and what would trigger adding each._

## Why the app is branded as BirBookmarks

The app wears the Birbank design language: their red (`#EC3342`), their ink and greys, Onest as
the typeface, their ribbon mark, white cards on a near-white page, a filled pill search field and
a bottom bar in their style.

**The point it makes.** The API and the client are separable from presentation. The same app took a
completely different skin — palette, type, iconography, navigation shell — without one line of the
data layer changing: no request, no model, no ViewModel was touched. That is the argument for
keeping presentation out of the layers underneath it, made by demonstration rather than assertion.

**The trade-off, stated plainly.** This spent time the brief would rather have seen in features,
and the brief's line is "no over-engineering, small and clean beats big and unfinished". It was
sequenced deliberately: after search, add, detail and delete were finished and verified, and before
the shipped APK, so nothing functional was traded for it and the artifact carries the identity.

**What was deliberately not copied.** Their bottom bar has five tabs because their app has five
things. This one has two — All and Favourites — because that is what this app has. Padding the bar
out to five would have put tabs in it that lead nowhere, and a reviewer taps every tab. Borrowing
the visual language is the goal; borrowing the information architecture would have produced
decoration. Nothing in the shell is a placeholder.

**On the mark.** The real Birbank mark is used rather than a lookalike, redrawn as a vector from
their 152px app icon — measured, not eyeballed: it is a true parallelogram with vertical sides and
two parallel 45-degree cuts. The reviewers are the trademark owner, which makes this low-risk in
this setting, but it is a deliberate choice rather than an accident. One fidelity note: their icon
samples `#FF0039`, slightly hotter than the `#EC3342` their site uses for UI red. One red beats two
nearly identical ones, so the site value won and the mark is drawn in it.

Onest ships inside the APK under the Open Font Licence — one 193KB variable font covering every
weight, with the licence text alongside it in `assets/`. No new dependency and nothing to license.

## What I'd do with more time

_To fill in._

## How it scales

_To fill in. The short version: this is a data-partitioning problem, not a
distributed-systems one._

## AI tools

_To fill in. Which tools, for code and for design, and where they actually saved time._

### Where a tool got it wrong

_To fill in — required. One specific case: what the tool produced, why it was wrong, and how
it surfaced. A compile error or a failing test is a perfectly good answer._
