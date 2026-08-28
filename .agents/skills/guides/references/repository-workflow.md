# Micronaut Guides Repository Workflow

This reference records the workflow to use after this skill triggers. Treat it as a checklist, not as a replacement for reading current upstream files.

## Clone and Branch

Create the guide work in `micronaut-projects/micronaut-guides`, not in the source module repository:

```bash
git clone https://github.com/micronaut-projects/micronaut-guides.git
cd micronaut-guides
git fetch origin
git switch master
git pull --ff-only
git switch -c <descriptive-guide-branch>
```

If a contributor PR already exists, preserve that branch and align documentation work to it instead of silently starting a replacement.

## Files to Inspect Every Time

Read current upstream files before authoring:

- `README.md`: repository structure, build commands, guide creation notes, macro examples, testing workflow.
- `build.gradle`: site asset flow and output directories.
- `buildSrc/src/main/groovy/io/micronaut/guides/GuidesPlugin.groovy`: dynamic task names and guide task wiring.
- `buildSrc/src/main/java/io/micronaut/guides/core/Guide.java`: metadata model.
- `buildSrc/src/main/resources/guide-metadata.schema.json`: JSON schema generated for metadata validation.
- `buildSrc/src/main/java/io/micronaut/guides/Category.java`: valid category labels.
- Nearby `guides/<slug>/metadata.json` and `<slug>.adoc` files in the same category or feature family.

## Output Directories

The Micronaut Guides build generates transient artifacts:

- `build/code`: generated application projects and `test.sh`.
- `src/doc/asciidoc`: temporary generated AsciiDoc after custom macro expansion.
- `build/docs/asciidoc`: Asciidoctor output before site theming.
- `build/dist`: rendered guide site, themed HTML, static assets, generated zips, index, tag pages, and per-guide pages.

Do not commit generated artifacts unless upstream maintainers explicitly request them.

## Dynamic Gradle Task Names

Guide-specific tasks use the guide directory slug converted from kebab-case to lowerCamelCase:

- `micronaut-http-client` -> `micronautHttpClientBuild`
- `micronaut-http-client` -> `micronautHttpClientRunTestScript`

Run:

```bash
./gradlew <guideLowerCamel>Build
./gradlew <guideLowerCamel>RunTestScript
```

Use full validation when shared build logic, reusable features, common snippets, templates, or broad site generation changed:

```bash
./gradlew build
```

## Validation Matrix

Choose the smallest validation that proves the change:

| Change | Minimum validation |
| --- | --- |
| New or updated regular guide | `<guideLowerCamel>Build` and `<guideLowerCamel>RunTestScript` |
| Prose-only AsciiDoc correction | `<guideLowerCamel>Build`, plus inspect rendered HTML |
| Metadata-only change | `<guideLowerCamel>Build`, plus inspect generated index/category behavior if relevant |
| Common snippet or macro usage shared by multiple guides | At least one affected guide build, and broader build if many guides are affected |
| `buildSrc` feature or dependency change | `./gradlew build` unless maintainers approve narrower validation |
| Cloud or credentialed guide | Build/render locally; run provisioning only with explicit confirmation |

Record skipped commands and the reason in the PR handoff.

## Topic Discovery Commands

Useful inventory commands from the `micronaut-guides` root:

```bash
find guides -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort
jq -r '.title, (.categories[]? // empty), (.tags[]? // empty)' guides/*/metadata.json
rg -n "<topic>|<api>|<annotation>|<feature>" guides buildSrc/src/main/java/io/micronaut/guides/feature src/docs/common
jq -r 'select(.publish == false) | input_filename' guides/*/metadata.json
rg -n '"base"' guides/*/metadata.json
```

When deduping, compare the proposed guide against published guide titles, guide slugs, tags, categories, related source files, and base/partial guides.

## Escalation Rules

Stop and ask for direction when:

- The proposed topic duplicates an existing guide.
- The guide requires a new Starter feature and it is unclear whether `buildSrc/src/main/java/io/micronaut/guides/feature` should own it.
- Validation depends on paid cloud resources, external accounts, production services, or secrets.
- The implementation behavior is not proven by local repository code or tests.
- The guide needs broad architecture or release-policy decisions outside a tutorial PR.
