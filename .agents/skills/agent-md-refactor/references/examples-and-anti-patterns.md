# Examples and Anti-Patterns

Use these examples to guide consistent refactors.

## Root File Pattern

Good root structure:

```markdown
# Project Instructions

One-line project description.

## Commands
- `pnpm build`
- `pnpm test`
- `pnpm typecheck`

## Detailed Guidance
- [Architecture](.agent-instructions/architecture.md)
- [Code Style](.agent-instructions/code-style.md)
- [Testing](.agent-instructions/testing.md)
```

Avoid:

```markdown
# AGENTS.md

## Code Style
... 150 lines ...

## Testing
... 120 lines ...

## TypeScript
... 180 lines ...
```

## Topic File Pattern

Good topic file:

```markdown
# Testing

## Scope
When to use each test type.

## Rules
- Use integration tests for persistence adapters.
- Keep unit tests deterministic.

## Examples
- Good: verifies behavior with stable fixtures.
- Avoid: brittle tests tied to timestamps.
```

## Anti-Patterns

- Keeping all details in root file.
- Splitting into too many tiny files with unclear ownership.
- Duplicating the same rule across files.
- Retaining vague guidance like "write clean code".
- Using absolute or agent-specific links when relative links work.

## Deletion Candidates

Flag these for removal during refactor:

- Rules that just restate defaults.
- Outdated references to removed tools/frameworks.
- Safety slogans without actionable behavior.
- Duplicate rules that conflict in wording.
