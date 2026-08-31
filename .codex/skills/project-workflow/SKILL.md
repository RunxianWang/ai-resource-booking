---
name: project-workflow
description: Follow the AI resource booking project's conventions for branch names, commits, PR scope, local startup, and acceptance testing.
---

# AI Resource Booking project workflow

Use this skill when changing this repository or discussing its implementation workflow.

## Branch naming

Use lowercase names with hyphens and a clear type prefix:

- `feature/<scope>` for new functionality
- `fix/<scope>` for bug fixes
- `refactor/<scope>` for behavior-preserving restructuring
- `chore/<scope>` for tooling or documentation

Examples:

- `feature/pr1-user-auth`
- `feature/pr2-same-day-multi-hour-booking`
- `fix/resource-load-error`

Do not use environment or agent names in branch names.

## Commit and PR scope

Use Conventional Commit style, for example:

- `feat: add user authentication and admin permissions`
- `fix: restore resource catalog loading`
- `docs: define same-day booking rules`

Keep one PR focused on one user-visible objective. PR1 covers authentication, roles, JWT, route protection, registration/login/logout, and the minimum administrator resource-maintenance permission. Multi-hour booking belongs in a separate PR.

## Acceptance and verification

Before reporting completion:

1. Run the relevant backend tests or compile check.
2. Run the frontend production build when frontend files changed.
3. Run `git diff --check`.
4. Test the changed API with both a normal user and an administrator when permissions are involved.
5. Record known test data, startup commands, and any remaining risks in the PR description or a focused `docs/` file.

Do not commit runtime logs, local secrets, generated output, or test-only database records unless explicitly requested.

## Current local startup

Run backend and frontend separately from the repository root:

- Backend: `mvn spring-boot:run`
- Frontend: `npm run dev --prefix src/frontend -- --host 127.0.0.1`

The normal local URLs are backend `http://localhost:8080` and frontend `http://localhost:5173`.

## Product boundary for booking work

The current inventory model uses one-hour slots. A future multi-hour feature should keep hourly inventory, accept a start time and duration, require contiguous available slots on the same day, and reserve them atomically. It must not be implemented by changing SQL alone.
