# Universal Skill Templates

Use these templates as starting points. Keep them concise and agent-agnostic.

## 1) Minimal `SKILL.md` template

```markdown
---
name: <skill-name>
description: <what this skill does and when to use it>
license: MIT
---

# <Skill Title>

## Goal

<one paragraph describing expected outcomes>

## Procedure

1. <step 1>
2. <step 2>
3. <step 3>

## Inputs

- <input 1>
- <input 2>

## Outputs

- <output contract>

## Edge cases

- <edge case 1 and handling>
- <edge case 2 and handling>

## Validation

- [ ] Frontmatter is valid YAML
- [ ] Name matches folder
- [ ] Description includes trigger contexts
- [ ] Relative file references resolve
```

## 2) Expanded structure template

```text
<skill-name>/
├── SKILL.md
├── scripts/
│   └── <script>.py
├── references/
│   ├── <domain>.md
│   └── <schemas>.md
└── assets/
    └── <template-or-static-file>
```

Use this expanded layout only if those resources are truly needed.

## 3) Trigger description pattern

Write descriptions that include both capability and activation cues.

Pattern:

"<Core capability>. Use when users ask to <intent 1>, <intent 2>, or <intent 3>, including phrasing such as <example cues>."

Example:

"Generate release notes from Git history with categorized changes. Use when users ask to draft changelogs, summarize commits for releases, or prepare version notes from tags or commit ranges."

## 4) Improvement loop template

When improving an existing skill:

1. Review current `SKILL.md` and user complaints.
2. Capture 2-3 should-trigger and 2-3 should-not-trigger prompts.
3. Revise `description` for better trigger clarity.
4. Move long details from `SKILL.md` to `references/`.
5. Re-validate against checklist in `references/spec-checklist.md`.
