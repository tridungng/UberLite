# UberLite MVP — Spec Package for GitHub Copilot Coding Agent

This package turns *"Designing UberLite: a Ride Aggregator Service"* (Prasaad & Vikström,
UW CSE552, Fall 2019) into a buildable MVP: a **true microservices system in Spring Boot**
covering every service the paper describes, with infrastructure simplified so it can be
built incrementally and run on a laptop.

## What's in here

| File | Purpose |
|---|---|
| `ARCHITECTURE.md` | The MVP spec: every service, its simplified scope, API contract, data model, and the infra substitutions made vs. the paper (H3 instead of a full map provider, Postgres instead of HBase, Kafka for triggers, etc). Read this first — it's the shared source of truth Copilot and you both work from. |
| `.github/copilot-instructions.md` | Drop this into your real repo at that exact path. GitHub Copilot's coding agent (and Copilot Chat agent mode) reads it automatically on every task and uses it as standing context — stack, conventions, module layout, "don't do this" list. |
| `issues/00`–`issues/11` | Twelve issues, in dependency order, each scoped to a single Copilot coding-agent run (roughly one PR each). Copy each one verbatim into a GitHub issue. |

## How to actually run this workflow

1. **Create the repo**, add `.github/copilot-instructions.md`, commit `ARCHITECTURE.md` at the root.
2. **File the issues** in order (00 → 11). Keep them as separate issues, not one mega-issue —
   Copilot's coding agent does best with a tightly scoped, independently-mergeable unit of work per issue.
3. **Assign issue 00 to Copilot** (via the issue's "Assignees" → `Copilot`, or `@copilot` in a comment).
   It will open a draft PR, work in a background session, and push commits as it goes.
4. **Review the PR like you would a junior engineer's** — Copilot's coding agent is good at
   mechanical scaffolding and following a spec, weak at architectural judgment calls it wasn't
   told about. Use PR review comments to redirect it; it'll iterate.
5. **Merge, then assign the next issue.** Each issue after 00 assumes prior ones are merged —
   don't parallelize the early infra issues (00–02); the service issues (03–09) can run in
   parallel once 00–02 are merged, since they don't depend on each other, only on shared contracts.
6. Issues 10–11 (integration + observability) should come last, after everything else is merged.

## Why this scope

The paper's full design (Kafka-triggered HBase trip store, Spark/Flink streaming, ML-based
forecasting/matching, a real map/traffic provider) is a production system, not an MVP. This spec
keeps **every service named in the paper** so the architecture is faithful, but replaces every
ML/big-data component with a rule-based or stub equivalent that's honest about being a stand-in.
`ARCHITECTURE.md` calls out each substitution explicitly so you know what to swap out later.