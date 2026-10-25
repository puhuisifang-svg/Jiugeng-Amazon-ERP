# Jiugeng Amazon ERP Agent Rules

This repository is a shared working directory used by the Jiugeng project-manager agent and the local Codex client. Treat every task as coordinated team work, not as an isolated personal checkout.

## Ownership and coordination

- The project-manager agent owns scope, task assignment, acceptance, merges to `main`, deployment, and release status.
- Codex acts as an implementation or review agent for an explicitly assigned task ID such as `ERP-006`.
- Only one agent may modify this working directory at a time. Read-only investigation may run in parallel, but concurrent edits, branch switches, commits, test processes that rewrite files, and service restarts are forbidden.
- Before changing anything, run `git branch --show-current` and `git status --short`. If the worktree contains changes that are not part of the assigned task, stop and report them; never overwrite, stash, restore, stage, or commit another agent's work.
- A dirty worktree is the handoff lock. Do not begin a second modifying task until the current owner has committed or otherwise completed its handoff and the worktree is clean.
- Do not create another worktree for normal project work. This shared checkout is the authoritative local workspace unless the project manager explicitly authorizes an exception.

## Branch and Git policy

- Start implementation from an up-to-date `origin/main` on a dedicated branch named `feature/ERP-<number>-<slug>` or `fix/ERP-<number>-<slug>`.
- Because a branch switch affects every agent sharing this directory, switch branches only at the start of an assigned write slot and only from a clean worktree.
- Keep commits atomic and use Conventional Commit subjects such as `feat(selection): ...`, `fix(import): ...`, `test(selection): ...`, or `docs(pjm): ...`.
- Never force-push, rewrite published history, delete branches, merge into `main`, or deploy unless the task explicitly assigns that action.
- Codex may commit and push its assigned feature branch when requested. The project-manager agent verifies the exact SHA, gates, and diff before merging.
- Preserve unrelated files and user changes. Never use broad restore, reset, clean, or recursive deletion commands.

## Project boundaries

- This is an internal Amazon ERP. Current implemented scope is the local synthetic/mock MVP; do not present synthetic data as production data.
- Backend: `deliverables/backend/sprint-0/jiugeng-erp-backend` using Java 21, Spring Boot, and Spring Modulith.
- Frontend: `frontend` using Vue, TypeScript, Vite, Vitest, and pnpm.
- Controlled product, governance, architecture, quality, security, and Sprint records live under `docs/` and `deliverables/`.
- Do not add real credentials, tokens, passwords, private keys, customer data, or production identifiers to code, fixtures, documentation, commands, logs, or commits.
- Keep module boundaries intact. Do not make one Spring Modulith domain depend on another module's internal types.
- Maintain append-only audit semantics where already established for review notes and evidence records.

## Required workflow

- All UI work must read and follow `docs/ui/UI_STANDARD.md`. Reuse shared density tokens and controls; record and justify any exception. Do not regress compact navigation, filters, action bars, sticky table headers, or responsive button visibility.

1. Read this file, the assigned ERP plan, and the directly relevant code/tests.
2. Confirm the task scope, current branch, base SHA, and clean worktree.
3. For a multi-file or cross-layer change, produce a short implementation plan before editing.
4. Implement only the assigned scope. Do not opportunistically refactor unrelated modules.
5. Add or update tests for changed behavior, including permission, validation, failure, and regression cases where applicable.
6. Run the proportional gates below. Fix failures caused by the task before handoff.
7. Inspect `git diff --check`, the final diff, and `git status --short`. Confirm no unrelated files are staged.
8. Commit atomically when requested, then report branch, full commit SHA, changed files, gate results, risks, and any intentionally deferred work.

## Quality gates

Backend full gate:

```bash
cd deliverables/backend/sprint-0/jiugeng-erp-backend
./mvnw clean verify
```

Frontend full gate:

```bash
cd frontend
pnpm quality
```

Import contract gate when those files change:

```bash
cd deliverables/backend/sprint-0/import-contract/tests
npm ci --ignore-scripts
npm test
python3 validate_contract.py
```

Also run:

```bash
git diff --check
```

Use narrower tests during iteration, but run every affected full gate before declaring completion.

## Local services

- Persistent local services are owned by the project-manager deployment flow in tmux session `jiugeng-mvp`.
- Do not stop, restart, replace, or reuse those service panes unless deployment or dynamic acceptance is explicitly assigned.
- Prefer one-shot tests. If a temporary service is necessary, use a non-conflicting port and stop it before handoff.
- A backend restart resets the current in-memory mock data; mention this before any assigned restart.

## Handoff contract

Every implementation handoff must include:

- task ID and branch;
- full commit SHA, or a clear statement that changes remain uncommitted;
- concise list of changed files and behavior;
- exact tests and quality gates run, with results;
- remaining risks, blockers, and deferred scope;
- confirmation that `git status --short` contains no unrelated changes.

Do not claim that a task is merged, deployed, or accepted. Those states are confirmed by the project-manager agent after independent verification.
