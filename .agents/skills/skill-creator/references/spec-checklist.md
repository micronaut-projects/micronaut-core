# Agent Skills Spec Checklist

Use this checklist when creating or modifying a skill to keep it compatible with the Agent Skills specification.

Primary source: https://agentskills.io/specification

## MUST requirements

1. Skill is a directory containing `SKILL.md`.
2. `SKILL.md` starts with valid YAML frontmatter.
3. Frontmatter includes required fields:
   - `name`
   - `description`
4. `name` rules:
   - 1-64 characters
   - lowercase letters, numbers, hyphens only
   - does not start/end with hyphen
   - no consecutive hyphens
   - matches parent directory name
5. `description` rules:
   - 1-1024 characters
   - non-empty
   - states what the skill does and when to use it
6. Markdown body exists after frontmatter.

## SHOULD guidance

1. Keep `SKILL.md` under ~500 lines for efficient context usage.
2. Use progressive disclosure:
   - keep core instructions in `SKILL.md`
   - move detailed material to `references/`
3. Add `scripts/` only for deterministic or repeated work.
4. Keep file references relative to skill root.
5. Keep references shallow and easy to discover from `SKILL.md`.

## Optional frontmatter fields

- `license`
- `compatibility`
- `metadata`
- `allowed-tools` (experimental; support varies by agent)

## Portability rules

1. Avoid vendor-specific instructions unless requested by the user.
2. Avoid agent-specific path assumptions if portable paths are possible.
3. Avoid relying on non-standard tools without fallback guidance.

## Validation commands

If available:

```bash
skills-ref validate ./<skill-name>
```

For discovery checks in a workspace using skills CLI:

```bash
npx skills list
```
