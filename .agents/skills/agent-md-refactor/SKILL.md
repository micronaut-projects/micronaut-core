---
name: agent-md-refactor
description: Refactor oversized agent instruction files into a progressive-disclosure structure. Use when users ask to split AGENTS.md/CLAUDE.md/COPILOT.md, reduce instruction bloat, or organize guidance into linked topic files.
license: Apache-2.0
compatibility: Repositories using markdown instruction files such as AGENTS.md, CLAUDE.md, or COPILOT.md
metadata:
  author: Álvaro Sánchez-Mariscal
  version: "1.0.0"
---

# Agent MD Refactor

Refactor monolithic instruction files into a compact root file plus linked topic files.
Keep this file focused on execution flow and load detailed templates/examples from references.

## Goal

Deliver a maintainable instruction system with:

- a minimal root instruction file containing only universal guidance,
- linked topic files for detailed rules,
- contradictions resolved and vague/redundant rules removed.

## Trigger Examples

Should trigger:

- "Refactor this AGENTS.md to use progressive disclosure."
- "Split my CLAUDE.md into focused instruction files."
- "My instruction file is too long, reorganize it."

Should not trigger:

- "Explain what AGENTS.md is."
- "Fix one typo in CLAUDE.md."
- "Write a brand-new coding standard from scratch."

More examples: [examples-and-anti-patterns](references/examples-and-anti-patterns.md).

## Procedure

1. Inventory instruction sources (`AGENTS.md`, `CLAUDE.md`, `COPILOT.md`, related guidance docs).
2. Detect contradictions and resolve by repository precedence.
3. Keep only root-level essentials in the root instruction file.
4. Group remaining content into 3-8 linked topic files with clear names.
5. Remove non-actionable, redundant, and outdated directives.
6. Validate links, coverage, and consistency before completion.

Detailed step playbook: [refactor-playbook](references/refactor-playbook.md).

## Inputs

- Root instruction file path(s)
- Existing repository conventions (commands, toolchain, style)
- User constraints (must-keep rules, preferred layout)

## Outputs

- Refactored root instruction file (minimal, universal guidance)
- Topic files with detailed rules
- List of removed/merged rules with rationale
- Notes on contradiction handling

## Edge Cases

- If rule precedence is impossible to infer, request one targeted user decision.
- If multiple files repeat the same rule, keep one canonical source and replace others with links.
- If the instruction file is already concise, avoid unnecessary splitting.
- Use relative markdown links for portability.

## Validation Checklist

- [ ] Frontmatter is valid YAML and `name` matches directory.
- [ ] Description states capability and usage triggers.
- [ ] Root file contains only universal instructions.
- [ ] Topic files are self-contained and linked from root.
- [ ] Contradictions are resolved or explicitly called out.
- [ ] Vague/default/redundant directives are removed or rewritten.
- [ ] Relative links resolve.

## References

- [Refactor playbook](references/refactor-playbook.md)
- [Examples and anti-patterns](references/examples-and-anti-patterns.md)
