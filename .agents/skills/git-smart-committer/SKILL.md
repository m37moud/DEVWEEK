---
name: git-smart-committer
description: Create safe, well-grouped Git commits from an existing working tree by inspecting status and diffs, separating unrelated changes, staging only the files or hunks that belong together, and writing strong descriptive commit messages. Use when Codex needs to plan commits, commit all safely, commit a specific feature or fix, prepare Conventional Commit messages, or explain which changes should remain unstaged.
---

# Git Smart Committer

## Overview

Inspect the working tree, split changes into logical commit groups, and create focused commits with clear messages. Keep unrelated, suspicious, generated, or unclear changes out of the commit set and explain what was skipped.

## Workflow

1. Inspect the working tree before staging anything.
2. Review diffs deeply enough to understand purpose, risk, and scope.
3. Group changes by intent, not by file type alone.
4. Prefer multiple focused commits over one mixed commit.
5. Stage only the files or hunks that belong to the current group.
6. Commit with a descriptive message, using Conventional Commits when they fit.
7. Report what was committed, what was skipped, and the final Git status.

## Inspect Changes

Run these commands in order unless the user explicitly narrows the scope:

```bash
git status --short
git diff --stat
git diff
```

Use targeted diffs for large change sets:

```bash
git diff -- path/to/file
git diff -- module-a/ module-b/
git diff --cached
```

Read enough of the diff to answer:

- What user-visible or developer-facing behavior changed?
- Which files belong to the same purpose?
- Which files are risky, ambiguous, generated, or unrelated?
- Whether tests, specs, docs, or migrations directly support the same change

## Existing Staged Changes

Before staging new files, inspect the current staged state:

```bash
git diff --cached --stat
git diff --cached
```

If staged changes already exist:

- Decide whether they belong to the same intended commit flow
- Preserve intentional staged work when it is already correct
- Avoid silently mixing new groups into unrelated staged changes
- If the staged set is unclear or conflicts with the proposed grouping, explain the conflict and show a plan before changing the index

## Decide Commit Groups

Group by purpose. Good groups include:

- Feature implementation plus its direct tests
- Bug fix plus regression tests
- Refactor-only changes
- Documentation-only changes
- Database migration plus the code that depends on it
- Build or configuration changes
- Test-only improvements

Avoid mixed groups such as:

- Unrelated module changes in one commit
- Documentation changes bundled with unrelated source changes
- Formatting noise bundled with logic changes
- Database migrations bundled with unrelated tests or refactors

When multiple logical groups exist, or when the scope is unclear, present a commit plan before committing. For each proposed group, include:

- Intended files
- Short rationale
- Proposed commit message
- Any files or hunks to leave unstaged

If the user says `show commit plan only`, stop after presenting the groups.

## Approval Rule

If more than one commit group is detected, show the commit plan first and wait for user approval unless the user explicitly said:

- `commit all safely`
- `create the commits now`
- `commit using best grouping`

If there is only one clear group, the skill may commit directly.

## Apply Safety Rules

Never commit:

- Secrets, credentials, tokens, private keys, or `.env` files
- Local IDE or system files
- Generated or build output unless intentionally tracked and clearly relevant
- Unrelated formatting-only churn mixed into logic changes

Be extra cautious with:

- Lockfiles
- Large documentation sweeps
- Database migrations
- Files with both refactor and behavior changes
- Files containing multiple unrelated hunks

If a file contains unrelated hunks, prefer precise staging only when it is safe and clear. Prefer non-interactive staging when possible. Use patch staging only when the environment supports it cleanly and the selected hunks are obviously correct. If staging part of a file feels risky or ambiguous, leave the file unstaged and explain why.

Never amend, rebase, reset, force-push, or rewrite history unless the user explicitly asks. Never push unless the user explicitly asks.

## Pre-Commit Verification

Before creating a commit, run lightweight verification when relevant and practical.

Use the smallest relevant check first:

- Documentation-only changes: no build required unless docs generation is affected
- Kotlin source or Android resource changes: use `./gradlew :app:assembleDebug` and relevant existing tests when practical
- Build or config changes: run the relevant Gradle check or build command
- Migration changes: run or mention migration validation if available
- Documentation or skill changes: verify Markdown paths and references where practical

If tests or checks are expensive, unavailable, or outside the requested scope, do not block forever. Report that verification was not run and explain why.

Never remove or weaken tests to make a commit pass.

## Stage And Commit

For each commit group:

1. Stage only the relevant files or hunks.
2. Recheck the staged diff with `git diff --cached`.
3. Run lightweight verification when relevant and practical.
4. Confirm no suspicious files slipped in.
5. Write the commit with a strong message.

Prefer Conventional Commits when suitable:

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`
- `build`
- `ci`
- `perf`
- `security`

Format:

```text
type(scope): concise summary
```

Add a body when it improves clarity. Focus on:

- What changed
- Why it changed
- Important behavior, risk, or scope boundary
- Related spec, gap, or task when useful

Choose scopes that reflect the actual area of change, such as `ui`, `data`, `playback`, `skills`, or `build`.

## Project-Specific Heuristics

Use the repository layout to reason about scope:

- `app/src/main/java/com/m37moud/responsivestories/**`: Kotlin implementation
- `app/src/main/res/**`: XML layouts, strings, drawables, and other Android resources
- `app/src/main/assets/**`: bundled media; preserve filenames and `AR`/`EN` folders
- `docs/**` and `app/store-assets/**`: release/design documentation and store materials
- `.agents/skills/**` and `AGENTS.md`: agent guidance and skills
- Gradle files and `gradle/libs.versions.toml`: build and dependency configuration

For this single-module Kotlin Android app using Activities, Fragments, and XML layouts:

- Keep Kotlin code and Android resources together when they implement the same feature
- Separate unrelated feature work even when it touches the same app module
- Pair tests with the feature or fix they verify
- Pair migrations only with the code that actually depends on them
- Keep docs with code only when they describe the same change set

## Request Variants

If the user says `commit all safely`:

- Still inspect and group changes
- Create multiple commits if needed
- Skip suspicious or unrelated files
- Report everything skipped

If the user says `commit this feature`:

- Infer the requested scope from the user request, branch, touched features, and diff
- Stage and commit only that feature's files
- Leave unrelated changes unstaged

If the user says `show commit plan only`:

- Do not stage
- Do not commit
- Return proposed groups and messages only

## Final Response

Return results in this format:

`## Commit Summary`
- `Commit hash: [hash]`
- `Message: [message]`
- `Files included:`
- `[file]`

`## Skipped / Unstaged Files`
- `[file]: [reason]`

`## Final Git Status`

```text
[git status output]
```

If multiple commits were created, repeat the `Commit Summary` block for each commit in commit order.
