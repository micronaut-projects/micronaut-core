---
name: skill-creator
description: Create new Agent Skills or improve existing ones in an agent-agnostic way. Use when users ask to build, refactor, validate, or package skills compatible with the Agent Skills specification and the skills CLI ecosystem.
license: MIT
compatibility: Compatible with Agent Skills spec and skill directories such as .agents/skills or equivalent agent-specific skill paths.
metadata:
  author: local
  version: "1.0.0"
---

# Skill Creator

Create and refine skills that work across agents implementing the Agent Skills specification.

Use imperative instructions. Keep the process deterministic where possible. Keep the skill output portable.

## Objectives

1. Produce a valid skill with a correct `SKILL.md` frontmatter and useful body instructions.
2. Keep the skill agent-agnostic unless the user explicitly requests agent-specific behavior.
3. Apply progressive disclosure so only essential content stays in `SKILL.md`.
4. Validate structure and naming before declaring completion.

## Workflow

### 1) Identify intent and scope

Determine whether the user wants one of these outcomes:

- New skill from scratch
- Update an existing skill
- Improve trigger quality (`description` tuning)
- Add reusable resources (`scripts/`, `references/`, `assets/`)

Extract known constraints from conversation context first. Only ask for missing details that materially change implementation.

### 2) Gather concrete examples

Collect at least 2 realistic user prompts that should trigger the target skill. Also collect at least 2 near-miss prompts that should not trigger it.

Use these examples to decide:

- Required workflow steps
- Output format expectations
- Edge cases to handle
- Whether deterministic scripts are needed

### 3) Design the skill anatomy

Create a minimal structure first:

```text
<skill-name>/
└── SKILL.md
```

Add optional directories only when justified:

- `scripts/` for deterministic or repeated operations
- `references/` for detailed docs, schemas, and long procedures
- `assets/` for templates and static resources used in outputs

### 4) Author `SKILL.md` frontmatter

Set required fields:

- `name`: lowercase, digits, hyphens; matches folder name
- `description`: what the skill does and when to use it

Common optional fields include `license`, `compatibility`, `metadata`, and `allowed-tools`. Additional frontmatter keys (for example, MCP or tooling configuration) are allowed when supported by the Agent Skills spec/validator, and may be nested under `metadata` when appropriate.

Description guidance:

- Include both capability and trigger cues
- Avoid vague phrases like "helps with X"
- Prefer concrete contexts and user-language synonyms

### 5) Author `SKILL.md` body

Structure the body for execution, not marketing:

1. Goal and success criteria
2. Step-by-step operating procedure
3. Input/output expectations
4. Error handling and edge cases
5. Examples
6. Validation checklist

Write in imperative style. Explain why non-obvious constraints matter.

### 6) Apply progressive disclosure

Keep `SKILL.md` concise. Move large or specialized content into `references/` and link to it from `SKILL.md`.

If the skill spans multiple variants, separate variant details into dedicated reference files and keep variant-selection logic in `SKILL.md`.

### 7) Validate and harden

Run a final compatibility pass:

- Frontmatter parses as valid YAML
- `name` complies with spec constraints and folder match
- `description` is explicit about trigger contexts
- Relative file references resolve
- Optional scripts are executable and documented
- Language is agent-neutral and avoids vendor lock-in

If `skills-ref` is available, run:

```bash
skills-ref validate ./<skill-name>
```

If the skills CLI is available, verify discovery from the repo root:

```bash
npx skills list
```

## Agent-Agnostic Rules

- Do not require a specific model vendor or proprietary UI affordance.
- Do not rely on agent-only file paths when portable paths exist.
- Do not reference unavailable tools as mandatory.
- Prefer open, portable commands and plain Markdown guidance.

## Safety and integrity

- Refuse to create skills intended for malware, exploitation, data exfiltration, or unauthorized access.
- Keep behavior aligned with user intent; avoid hidden actions.

## Completion criteria

A skill-creation task is complete only when all are true:

1. Skill folder exists in the requested location.
2. `SKILL.md` is valid and complete.
3. Optional resources are present only if needed.
4. Validation checks pass or limitations are explicitly reported.

## References

- See `references/spec-checklist.md` for implementation rules distilled from the spec.
- See `references/templates.md` for copy-ready scaffolds.
- See `references/research-synthesis.md` for design rationale from cross-source research.
