# Jiugeng Amazon ERP Agent Rules

This repository is a shared working directory used by the Jiugeng project-manager agent and the local Codex client. Treat every task as coordinated team work, not as an isolated personal checkout.

## Start here after a new session or model handoff

- Read `README.md`, `docs/04-engineering/PROJECT_HANDOVER.md`, and `docs/08-project-management/STATUS_REPORT.md` before planning work. Then read the assigned task and directly relevant code/tests. For UI work also read `docs/ui/UI_STANDARD.md`, including its later dated amendments.
- Do not rely on chat memory, model identity, the `sprint-0` directory name, or old Sprint/MVP documents to infer current implementation or deployment. The handover guide maps current modules; the status report records dated evidence and unresolved work.
- Recheck branch, worktree, base SHA and task ownership every time. A historical clean-worktree statement is not a write-slot grant. Resume uncommitted work only when ownership and scope are established.
- Distinguish implemented, tested, merged, deployed and business-accepted states. Verify the production workflow's actual deploy step and the intended site's `/version.json` before reporting a version as live. Never copy tokens, private runtime configuration or business data into handover documents.
- Before handoff, update the status report for material behavior, release evidence or unfinished work; update the handover guide when module ownership, workflows or investigation paths change. Use `templates/SESSION_HANDOFF_TEMPLATE.md` for an interrupted or multi-session task. Record exact commands/results, not just “tests passed.”
- Documentation-only changes require link/path and `git diff --check` validation. Application gates remain required for affected application changes. These reading/maintenance requirements do not authorize merges, production releases or concurrent edits. Local service restarts have standing user authorization below.

## Ownership and coordination

- The project-manager agent owns scope, task assignment, acceptance, merges to `main`, deployment, and release status.
- Codex acts as an implementation or review agent for an explicitly assigned task ID such as `ERP-006`.
- Only one agent may modify this working directory at a time. Read-only investigation may run in parallel, but concurrent edits, branch switches, commits, test processes that rewrite files, and service restarts are forbidden.
- Before changing anything, run `git branch --show-current` and `git status --short`. If the worktree contains changes that are not part of the assigned task, stop and report them; never overwrite, stash, restore, stage, or commit another agent's work.
- A dirty worktree is the handoff lock. Do not begin a second modifying task until the current owner has committed or otherwise completed its handoff and the worktree is clean.
- Do not create another worktree for normal project work. This shared checkout is the authoritative local workspace unless the project manager explicitly authorizes an exception.

## Branch and Git policy

- User release ownership update (2026-11-21): after local code validation and requested handoff, PJM alone owns submission/merge, CI/CD, deployment and release-status verification. Codex must not poll, wait for, or track online releases. Historical evidence requirements apply to PJM or a separately assigned investigation, not routine implementation.
- User release instruction (2026-11-20): complete subsequent system changes and validation locally, then wait for the user's explicit release instruction. Only after that instruction should Codex hand the reviewed changes to the project-manager agent to submit/merge into `main` and deploy production. A request to implement or test is not release authorization. Do not independently push, merge, trigger production workflows, or ask the project manager to release before that instruction; merging to `main` can trigger production deployment automatically. Local commits still follow the existing “when requested” rule.
- Start implementation from an up-to-date `origin/main` on a dedicated branch named `feature/ERP-<number>-<slug>` or `fix/ERP-<number>-<slug>`.
- Because a branch switch affects every agent sharing this directory, switch branches only at the start of an assigned write slot and only from a clean worktree.
- Keep commits atomic and use Conventional Commit subjects such as `feat(selection): ...`, `fix(import): ...`, `test(selection): ...`, or `docs(pjm): ...`.
- Never force-push, rewrite published history, delete branches, merge into `main`, or deploy unless the task explicitly assigns that action.
- Codex may commit and push its assigned feature branch when requested. The project-manager agent verifies the exact SHA, gates, and diff before merging.
- Preserve unrelated files and user changes. Never use broad restore, reset, clean, or recursive deletion commands.

## Project boundaries

- This is an internal Amazon ERP with persistent business modules and a production delivery pipeline, alongside retained mock/test paths. See the dated status report for the last verified release; do not present synthetic data as production data or infer production acceptance from code presence.
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
6. At the user-requested project-manager handoff, run the proportional gates below for the accumulated iteration changes. Fix failures caused by the iteration before handoff; follow the validation cadence below during implementation.
7. Inspect `git diff --check`, the final diff, and `git status --short`. Confirm no unrelated files are staged.
8. Commit atomically when requested, then report branch, full commit SHA, changed files, gate results, risks, and any intentionally deferred work.

## Quality gates

### Validation cadence (user agreement, 2026-11-20)

- One small iteration ends when the user requests handoff to the project-manager agent. Accumulate the user's local changes and pending checks within that iteration; do not treat each small feature as a separate full-validation cycle.
- During implementation, do not automatically run full gates, regression suites or the browser viewport matrix after each feature. Run a narrowly scoped check only when an actual error or uncertain critical behavior needs investigation; maintain relevant test coverage as part of the changes.
- Before handoff, assess the complete accumulated diff and consolidate the affected full gates, regression checks and UI verification. Reuse existing passing results when the covered code and dependencies have not changed. Repeat checks only for fixes, new changes or unresolved failures that invalidate the results.
- Until consolidated validation, report changes as locally implemented with validation pending, and keep the pending-check list in the status/handoff record. Handoff validation does not itself authorize commits, pushes, merges or deployment.

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

Run affected full gates at the iteration handoff boundary, not at each feature completion. Documentation-only changes do not require application gates.

## Local services

- Standing user authorization (2026-11-21): Codex may start, stop, restart and update persistent local services as needed for local development, without asking for confirmation each time. Keep using tmux session `jiugeng-mvp` and the existing local runtime configuration; do not run competing instances on the same ports or restart concurrently with another writer.
- The local MySQL profile persists business data across application restarts. Preserve its database volume, uploaded files and credentials; restarting does not authorize clearing or rebuilding data. Only an in-memory/mock profile may reset temporary data on restart; verify the active profile before describing the impact.
- Before applying local database migrations, make a local database backup and retain the previous runtime artifact/configuration. After a restart, check service health and the relevant runtime version or interface. Routine local restart checks do not trigger full regression suites.
- Prefer one-shot tests. If a temporary service is necessary, use a non-conflicting port and stop it before handoff.
- This authorization applies only to local services. Commits, pushes, merges into `main`, production deployments and production restarts still require the applicable explicit user instruction and PJM release process.

## Handoff contract

Every implementation handoff must include:

- task ID and branch;
- full commit SHA, or a clear statement that changes remain uncommitted;
- concise list of changed files and behavior;
- exact tests and quality gates run, with results;
- remaining risks, blockers, and deferred scope;
- confirmation that `git status --short` contains no unrelated changes.

Do not claim that a task is merged, deployed, or accepted. Those states are confirmed by the project-manager agent after independent verification.
