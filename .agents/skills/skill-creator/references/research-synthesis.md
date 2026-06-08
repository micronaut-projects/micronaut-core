# Research Synthesis

This note captures the design decisions used for `skill-creator`.

## Sources

- Agent Skills specification: https://agentskills.io/specification
- Anthropic skill-creator reference:
  https://github.com/anthropics/skills/tree/main/skills/skill-creator
- OpenAI skill-creator reference:
  https://github.com/openai/skills/tree/main/skills/.system/skill-creator
- Skills CLI reference:
  https://github.com/vercel-labs/skills

## Shared patterns extracted

1. Core process is iterative: scope, draft, test, refine.
2. Trigger quality depends primarily on frontmatter description clarity.
3. Progressive disclosure is essential for context efficiency.
4. Optional resources should be added only when they reduce repeated work.

## Vendor-specific elements intentionally avoided

1. Model/provider-specific instructions and path assumptions.
2. Dependence on platform-specific UI workflows.
3. Tooling that only exists in one agent runtime.

## Decisions in this skill

1. Keep the main workflow in `SKILL.md` as a portable baseline.
2. Move strict checklists and scaffolds into `references/` files.
3. Include skills CLI validation guidance (`npx skills list`) and optional spec validator guidance (`skills-ref validate`).
4. Keep language imperative and implementation-focused.
