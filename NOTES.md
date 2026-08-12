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
