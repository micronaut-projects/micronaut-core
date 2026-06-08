# Refactor Playbook

Use this playbook to execute a deterministic refactor from monolithic instruction files to progressive disclosure.

## Phase 1: Contradictions

Find conflicts across instruction files:

- style conflicts (`use semicolons` vs `no semicolons`)
- workflow conflicts (`always run full test suite` vs `run only targeted tests`)
- tool conflicts (`pnpm` vs `npm`)

Capture using:

```markdown
## Contradiction

- Instruction A: <quote + file>
- Instruction B: <quote + file>
- Proposed resolution: <inferred precedence>
- Needs user decision: <yes/no>
```

Escalate to user only when precedence cannot be inferred from repository conventions.

## Phase 2: Root Essentials

Keep only universal material in root:

- one-line project description
- canonical commands (build/test/typecheck/dev)
- package manager only if non-default
- critical global overrides applying to all tasks

Move everything else to topic files.

## Phase 3: Topic Grouping

Target 3-8 files. Common categories:

- `architecture.md`
- `code-style.md`
- `testing.md`
- `typescript.md`
- `git-workflow.md`
- `security.md`

Rules:

1. One topic per file.
2. No cross-file contradictions.
3. Use concrete imperative rules, not slogans.

## Phase 4: Structure

Preferred structure:

```text
project-root/
├── AGENTS.md (or CLAUDE.md/COPILOT.md)
└── .agent-instructions/
    ├── architecture.md
    ├── code-style.md
    ├── testing.md
    └── ...
```

Use relative links from root to each topic file.

## Phase 5: Prune

Remove statements that are:

- redundant with repository defaults
- vague and non-actionable
- generic safe-language with no behavioral effect
- obsolete/outdated

When pruning, keep a short "removed rules" note with reasons.
