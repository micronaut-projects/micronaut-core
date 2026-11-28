# Micronaut module maintenance tasks

## Triage incoming issues

New issues need to be categorized. At least with one of the following labels:

* `type: bug`: when something is not working as designed.
* `type: improvement`: a minor improvement over an existing feature.
* `type: enhancement`: a completely new feature.
* `type: docs`: documentation change.

There are other labels that are useful for changelog generation:

* `type: breaking`.
* `type: deprecated`.
* `type: removed`.

Issues with the above labels will show up in their own section in the changelog.

Sometimes, before accepting bugs, we need to ask more information to the requester, or need to validate ourselves that it
is actually a bug. There are some labels to help with these situations:

* `status: awaiting feedback`: used to mark that we are waiting for more information to the user.
* `status: awaiting validation`: we need to validate ourselves that it is actually an issue.
* `status: awaiting third-party`: the issue is blocked by another bug in a third-party library

Note that when the blockers are cleared, the awaiting labels need to be manually removed. There are some other labels
around this:

* `status: validated`: the issue is ready to be being worked on.
* `status: acknowledged` (possibly duplicate?).
* `status: in progress`: (could be removed, we assign issues to mark them being worked on)

There are sometimes where we are not sure whether we want or can solve an issue. The labels about this are:

* `status: under consideration`: the issue is being considered, but has not been accepted yet.
* `status: future consideration`: we won't fix it now (because either we can't or we don't want to), but this can be
  revisited in the future.
* `status: next major version`: it is a breaking change and therefore needs to be implemented in the next major version.

There are also a bunch of `relates-to` labels that can be used to further categorize issues. This is helpful in projects
with a lot of issues, or projects where different people work on different parts or modules.

The majority of the issues are defined in the
[management](https://github.com/micronaut-projects/management/blob/master/labels.tf) repo, and propagated via Terraform.
If you want new labels:

* If they can be beneficial to several repos, send a pull request to the management repo.
* If they are repo-specific, just go ahead and create them with the GitHub UI.

Finally, issues (especially bugs) should be prioritized with either `priority: high`, `priority: medium` or
`priority: low`. Checkout the
[Issue Priority Labels](https://github.com/micronaut-projects/micronaut-core/wiki/Issue-Priority-Labels) document for
guidelines about when to use each of them.

## Review pull requests

Pull requests, regardless of whether they are created by internal or external contributors, should meet the following
criteria:

* All the GitHub checks are passing (CLA signed and builds passing).
* Code has a minimum quality, it uses the Micronaut APIs correctly, doesn't contain bad smells, etc. Essentially, the
  type of things you would review in every other software project.
* Contains tests.
* Includes documentation.
* If it closes any issues,
  [they should be linked](https://docs.github.com/en/free-pro-team@latest/github/managing-your-work-on-github/linking-a-pull-request-to-an-issue)
  either using closing keywords, or manually.

Regarding the target branch, backwards-compatible bug fixes and improvements typically target the default branch,
backwards-compatible enhancements target the next minor version branch, and breaking changes target the next major version
branch. Check the
[Micronaut Module Versioning](https://github.com/micronaut-projects/micronaut-core/wiki/Micronaut-Module-Versioning)
document for more information.

Before merging pull requests, it is really important to ensure they target the correct branch, so that in the next
patch/minor release we don't leak breaking changes. Check the
[Micronaut Module Branch Naming](https://github.com/micronaut-projects/micronaut-core/wiki/Micronaut-Module-Branch-Naming)
document for more information.

Note that
[Micronaut Core and Starter](https://github.com/micronaut-projects/micronaut-core/wiki/Micronaut-Core-and-Starter-Branching-Strategy)
follow a slightly different strategy.

### Risk tiers and AI disclosure

The pull request template (`.github/PULL_REQUEST_TEMPLATE.md`) asks for a short description in fixed sections: what
changed, why, how to test, screenshots where they apply, and a three-item checklist (tests, documentation, target
branch). Sections that do not apply are deleted, and tool footers such as "Generated with ..." are left out, since the
declaration records AI use. The synced `.claude/settings.json` turns off Claude Code's pull request footer and the session
link in commits, and keeps its `Co-Authored-By` commit trailer. Footers that other tools or Claude's cloud sessions add
are tolerated: no check fails on them.

Every pull request declares four values in the "Review declaration" table of the template, one row per field, so
that workflows and the AI reviewer can read them back:

| Field                       | Value                              |
|-----------------------------|------------------------------------|
| **AI use**                  | none / assisted / agent-generated  |
| **AI tool**                 | tool name, or n/a                  |
| **Risk tier**               | T0 / T1 / T2                       |
| **API or behaviour change** | yes / no                           |

A row that still lists several options, or is missing, counts as not declared.

* **AI use**: `none` when no AI tool wrote any part of the change; `assisted` when a person wrote it with AI help
  (completion, chat, review suggestions); `agent-generated` when an agent wrote most of it and a person reviewed it.
  The pull request gets the matching `ai-use: none`, `ai-use: assisted` or `ai-use: agent-generated` label.
* **AI tool**: for example Claude Code, Codex or Copilot.
* **Risk tier**, which decides how much human review a pull request needs:
  * `T0`: build, docs, tests and synced files. Nothing published changes.
  * `T1`: internal module code.
  * `T2`: public API, a change of default behaviour, or anything labelled `type: breaking`, plus the sensitive areas
    each repository names in its own rules, such as dependency injection and HTTP in micronaut-core.

  The "Risk tier" workflow (`.github/workflows/risk-tier.yml`) classifies every pull request with
  [TypeSafe's Jev model](https://docs.typesafe.ai) and sets a `risk-tier: T0`, `risk-tier: T1` or `risk-tier: T2`
  label. Jev gets the title, description, labels, changed file names with their line counts, the changed lines of
  main source files (T2 paths first, up to about 15k tokens; tests, docs and build files are left out) and the T2
  paths. It picks a tier against the tier descriptions of the rules below, with a probability per tier, and the
  workflow takes the highest tier that reaches `round_up_at`, so an unsure answer rounds up. Jev also answers whether
  the diff changes public API, as the rules define it, and whether it breaks users. Either at `diff_t2_at` or more
  makes the pull request T2, and these answers never lower a tier. They are an early warning: the build's binary
  compatibility check stays the definitive test for binary breaks. The `risk-tier` check fails when the declared tier
  is lower than the classified one, and when Jev cannot be reached, the `TYPESAFE_API_KEY` organisation secret is
  missing or the rules are invalid: it never guesses a tier. The workflow summary names the model version and the rule
  files, and the tier that path rules alone give.

  Draft pull requests are not classified: the workflow skips them and runs when a pull request is marked ready for
  review. Bots are never sent to Jev. A Renovate pull request gets the tier of the update it makes, read from the update
  table in its description: patch, digest and pin updates are T0, minor T1 and major T2, and a grouped pull request
  takes the highest of its updates (`[bots.renovate] update_tiers`). Any other bot, such as micronaut-build's files
  sync or a GitHub App, gets the tier of the path rules alone. Jev is asked once per run, with all its questions in one
  request, and not again while the title, the description and the set of changed files stay the same: its last answer
  is kept in a workflow artifact for 30 days, so a push that only edits files the pull request already changed reuses it. After a
  push the workflow waits `debounce_seconds` (3 minutes by default, 0 turns it off) before asking Jev, and a newer push
  cancels the waiting run, so a burst of pushes costs one call. The check stays pending during the wait.

  The rules are in two TOML files with the same format:

  * `.github/risk-tiers.global.toml`, the rules for every repository. It lives only in this template and is not
    synced: the workflow fetches it from this repository's `master` on every run, so a merged change applies
    everywhere at once, and the check fails if it cannot be fetched.
  * `.github/risk-tiers.toml`, optional and kept in each repository, never synced. Its values overwrite the global
    ones: tables merge key by key, and any other value it sets, lists included, replaces the global one. A repository
    that sets `[tiers.T0] paths`, for example, must list every T0 glob it wants.

  Each tier has a `description`, which is what Jev reads, so classification is tuned by editing text. `paths` globs
  (gitignore-style) give each file a tier hint, T2 first, then T1, then T0, else `default_tier`. Changes under a T2
  path, or a pull request with one of the T2 `labels` (`type: breaking` by default) or "API or behaviour change: yes",
  are T2 whatever Jev answers. For example, micronaut-core could have:

  ```toml
  [tiers.T2]
  description = """Public API, dependency injection, annotation processing, HTTP, a change of default behaviour, or \
  anything breaking."""
  paths = ["inject/src/main/**", "core-processor/src/main/**", "http*/src/main/**"]
  ```

  Some paths are T2 in every repository, for people and bots alike: the tier rules and classifier
  (`.github/risk-tiers.global.toml`, `.github/risk-tiers.toml`, `.github/scripts/risk-tier.py`,
  `.github/workflows/risk-tier.yml`), and what CI, releases and the build run (`.github/workflows/**`,
  `.github/actions/**`, `.github/scripts/**`, `CODEOWNERS` in any of its three places, `buildSrc/**`, `gradle/wrapper/**`, `gradlew*`,
  `settings.gradle*`). No rules file can remove them, and a pull request's own copies of these files never classify
  it: the workflow runs the base branch's script and the template's global rules. Renovate updates to GitHub Actions
  or the Gradle wrapper are therefore T2.

  Jev can raise the tier the path rules give, but not lower it: main sources stay at least T1 whatever it answers.
  Anyone who can label may relabel a pull request to a higher tier. Only people with write access may relabel it
  lower, and never below T2 when it touches the paths above.

  Prefer `src/main` globs over whole module directories, since a test change under a T2 path also becomes T2.
  `python3 .github/scripts/risk-tier.py replay <owner/repo> --since <date>` classifies past pull requests without
  labelling them (pass `--repository-rules` for a rules file that is not checked out), and `--rules-only` uses the
  path rules instead of Jev, to compare.

  The labels are the classification of record, and Jev's tier is only a first guess. The AI reviewer double-checks
  it, and to reclassify a pull request the reviewer or a maintainer just relabels it, up or down. The workflow keeps
  any `risk-tier:` or `ai-use:` label that someone else added and only replaces its own.
* **API or behaviour change**: `yes` when public API or user-visible behaviour changes. `yes` means T2.

Every pull request gets an AI first pass before human review, reported as the "AI first pass" check. The AI reviewer
comments and requests changes, but it never approves: approval stays with maintainers.

### Review policy

The `review-policy` check says whether a pull request has the reviews its tier needs. It runs in `risk-tier.yml` after
the labels are set, and again in `review-policy.yml` on every submitted or dismissed review:

* T0: no person's review. The AI first pass is enough, and the pull request may merge itself.
* T1: one approval from a maintainer.
* T2: one approval from a maintainer and, for each changed file that the repo's `CODEOWNERS` assigns, an approval from
  one of its owners. Repos without `CODEOWNERS` need any maintainer.

A maintainer is anyone in `[review] maintainers`, or anyone with write access to the repo. The list is in the global
rules, and a repo's own rules can replace it. A change
request from a maintainer blocks every tier until it is dismissed or followed by an approval. When no one has been asked
to review a T1 or T2 pull request yet, the check requests the maintainer with the fewest open review requests in the
organisation, never the author. GitHub requests code owners by itself, so a T2 pull request in a repo with `CODEOWNERS`
goes to its owners.

Only T2 areas go in `CODEOWNERS`, so that the rotation spreads the rest. Its owners are people, not teams: the
workflow's token cannot read team membership, so any maintainer's approval covers a team's files. On a T2 pull request,
the check notes changed files under the repo's T2 paths that `CODEOWNERS` leaves without an owner.

A repo enforces the policy by setting `enforce = true` under `[review]` in its `.github/risk-tiers.toml`. Until then the
check only reports what it would need, passes, and requests no one. Setting it in the global rules enforces it everywhere.

An approval stays valid after later pushes, as with GitHub's own review rules. Like every required check that GitHub
Actions reports, a pull request that adds a workflow with a job of the same name can report a pass of its own, so a
maintainer who merges still reads the diff. Pull requests opened before the ruleset sync get the check on their next
push, edit or label change.

External contributors do not have to fill in the declaration. If it is left empty, the AI reviewer asks once and sets the
tier itself, and the pull request is not blocked on the AI use field.

### Automated pull requests

#### Dependency upgrades

All Micronaut repos have 2 dependency upgrade checking mechanism:

1. Renovate: it has the advantage that it performs dependency upgrades not only on build dependencies, but also on
   GitHub Actions workflows. On the other hand, its biggest downside is that it's unable to find newer versions for
   those defined in `gradle.properties`. It will also send different PRs for the same version upgrade if the artifact ID
   is different. To keep that down, the shared rules group Gradle and Maven updates by kind: one
   pull request for all patch updates, which Renovate merges itself once every check on it has passed (`platformAutomerge`
   is off, so a repository without required checks never merges before CI), and one pull request per Maven group
   ID for minor updates, so unrelated libraries never ship together. Those need a maintainer's review. Patch updates
   of 0.x versions go with the minor updates of their group ID. Major updates are also grouped per group ID, in pull
   requests separate from the minor ones, since a library family usually has to move to a new major at once.
   Micronaut modules keep their own group, and so do GitHub Actions.

   The rules live in `.github/renovate-global.json`, only in this template and never synced. The synced
   `.github/renovate.json` only extends it (`local>micronaut-projects/micronaut-project-template//.github/renovate-global`),
   and Renovate reads the template's `master` on every run, so a merged change reaches every repository at its next
   Renovate run. A repository that needs more keeps the `extends` line, adds its own settings after it, and keeps a
   `.github/renovate.json.lock` beside the file so the sync leaves it alone. Its `packageRules` come after the shared
   ones and win where both match, and its other settings replace the shared values, while later changes to the shared
   rules still reach it. Repositories outside the files sync can use the same one-line file. Changes to the shared
   rules are T2.

2. To overcome those disadvantages, we have our own dependency upgrade solution based on the
   [Gradle Use Latest Versions Plugin](https://github.com/patrikerdes/gradle-use-latest-versions-plugin). It runs daily
   during weekdays.

The consequence of having both approaches in place is that we get multiple dependency upgrade PRs: one created by
`micronaut-build` via our automation, and one or many (one per dependency) created by Renovate. When merging those, it
is better to prefer the `micronaut-build` ones, if possible, for 2 reasons: a) they attempt to upgrade multiple dependencies
in a single PR, which creates less noise in the Git history; b) Once you merge that, Renovate will react and automatically
close its own PRs if the dependency is up-to-date.

When an upgrade to a new version arrives, we need to be careful when merging, so that we don't introduce an
unnecessary upgrade burden on our users. Read the
[Module Upgrade Strategy](https://github.com/micronaut-projects/micronaut-core/wiki/Module-Upgrade-Strategy) for more
information.

Note that if a new version arrives, and we are not ready yet to do the upgrade, you need to
[pin the old version](https://github.com/micronaut-projects/micronaut-build/#configuration-options), because otherwise,
Renovate and our workflow will keep sending PRs. You should also create an issue to upgrade so that it's not forgotten.

#### Files sync

We have a [template repo](https://github.com/micronaut-projects/micronaut-project-template) that we use as the single
source of truth for certain files. It is used as a template to create new repos, and changes to certain files in the
template repo will get propagated automatically to every repository listed in `files-sync.yml`. Add a repository
created from the template to that list, by its name without `micronaut-`, or under `other_repos` when its name does
not start with `micronaut-`. The files propagated are:

* Workflow files (`.github/workflows/*`). They are copied using rsync:
  * `central-sync.yml`.
  * `graalvm-dev.yml`.
  * `graalvm-latest.yml`. Java CI (`gradle.yml`) calls it after its build job passes, so a repository that keeps its own
    `gradle.yml` must add the `native` job itself.
  * `gradle.yml`.
  * `publish-snapshot.yml`.
  * `release.yml`.
  * `sonatype.yml`.

* Renovate configuration (`.github/renovate.json`), which extends the template's shared rules.
* Claude Code project settings (`.claude/settings.json`), which turn off Claude Code's pull request footer and
  commit session link, keeping the `Co-Authored-By` trailer. A repository that needs its own settings keeps a `.claude/settings.json.lock` beside the file.
* Gradle wrapper.
* `.gitignore`.
* Issue templates (`.github/ISSUE_TEMPLATE/*`), the pull request template (`.github/PULL_REQUEST_TEMPLATE.md`), `LICENSE`,
  `MAINTAINING.md`, `config/HEADER` and `config/spotless.license.java`.
* Checkstyle's `config/checkstyle/checkstyle.xml` and `config/checkstyle/suppressions.xml`.

The sync opens an "Update common files" pull request in each repo. Every hour, `sync-automerge.yml` in the template repo
squash-merges each one that targets the default branch and whose checks passed, whatever its risk tier: the required
checks when the branch has any, all checks otherwise. Pull requests with failing checks, or that a branch rule refuses
(for example one requiring an approving review that micronaut-build cannot bypass), stay open for a maintainer, and the
run's summary lists them.

Regarding the Gradle wrapper, the template repo checks every week if there is a new Gradle version. If there is, it will
upgrade the wrapper in the template repo itself, and via the files sync workflow this gets propagated to all repos. This
way we make sure we stay up-to-date regarding Gradle versions in all repos.

##### Customised workflow files

The files sync workflow copies the workflow files listed above verbatim, so it is not possible to have custom steps in
them and still be part of the sync process: any modification to those files will be overwritten the next time the files
sync workflow is executed.

The "Java CI" (`gradle.yml`) workflow does have the ability to have an optional setup step, though. If there is a `setup.sh`
file in the project root, it will be executed before invoking Gradle.

There are projects, such as micronaut-gcp and micronaut-kubernetes, that have made customizations to sync'ed workflows
because it's absolutely necessary. In those projects, the sync pull requests are manually merged so that the customizations
aren't lost. When merging them, keep `gradle.yml` and `graalvm-latest.yml` in step: Java CI calls GraalVM Latest CI as a
reusable workflow, so taking only one of the two either stops the native tests or breaks Java CI.

Note that it is perfectly possible to have new workflows that aren't part of the sync process.

#### Org sync

Files are only synced to the modules listed in `files-sync.yml`, which share the template's Gradle build. Settings are
synced to every repository of the organisation that is not archived: every night, `org-sync.yml` in the template repo
applies the shared rulesets and labels to each one, changing them directly rather than through a pull request. Run it
by hand for one repository, or with "dry run" to see what it would change. A repository opts out with the topic
`org-sync-no-rulesets` or `org-sync-no-labels`, which only its admins can set. The temporary private forks of security
advisories (`*-ghsa-*`) are always skipped, and private repositories get no rulesets, because GitHub's Free plan has no
branch rules for them.

Labels: `.github/labels.json` in the template repo lists each
shared label with its colour and description. Every run creates the missing ones and updates the colour and description
of existing ones in each repo. A label can list `aliases`, such as GitHub's default `bug` for `type: bug`: an alias
found in a repo is renamed to the shared label, or, when both exist, its issues and pull requests get the shared label
and the alias is deleted. Other repo-specific labels are left alone. To rename a shared label, change its `name` and
add the old name to its `aliases`. `labels.json` itself is not copied to other repos; the sync runs
`.github/scripts/sync-labels.sh`, which takes `--dry-run` and retries each GitHub call three times before failing.

Branch protection is synced from `.github/rulesets.json` in the template repo (not copied) through
`.github/scripts/sync-rulesets.sh`, which takes `--prune` and `--dry-run`. It creates or updates three repository
rulesets:

* "Micronaut branches" blocks deleting and force-pushing the default branch and every `x.y.x` branch.
* "Micronaut required checks" requires `Java CI passed` and `license/cla` on the default branch. `Java CI passed` is
  the last job of the synced `gradle.yml`, and fails when any other Java CI job fails, so changing the JDK matrix never
  breaks the rule. The ruleset is only applied once the repo's `gradle.yml` has that job, so a repo whose sync pull
  request is not merged, or that keeps its own `gradle.yml`, is not blocked.
* "Micronaut review policy" requires the `review-policy` check (see "Review policy") on the default branch, once the
  repo's `risk-tier.yml` has that job. The check passes in repos that do not enforce the policy.

Organisation and repository admins can bypass all three. These are the only branch rules: the sync deletes every other
ruleset of a repo and every classic branch protection rule, so a rule added by hand in one repo is gone the next
night. So that a repo never loses its required checks, it keeps its old rules while one of them requires status checks
and "Micronaut required checks" does not apply yet. A repo with its own CI gets there by adding a job named
`Java CI passed` to its `gradle.yml`. Change the rules in `rulesets.json` instead. Managing rulesets needs admin on the repo, so the `GH_TOKEN` of the
sync needs it in every repository.

## Releases

The release process is highly automated and normally involves just publishing a GitHub release. But before you get there,
there are some parts you need to understand first.

First of all, all the repos have an automatic changelog generation mechanism: when a change is made to the repo
(a push event), it creates (or updates if there is already one) a draft release, calculating the next patch version. The
release notes will contain pull requests merged and issues closed since the last release.

When the module is ready for a new release, check the generated release notes, and make changes if needed (for example,
you can add an introduction paragraph highlighting some items included in the release). If the version you are going to
publish is not a new patch version, but a new minor or major, update the release notes text to reflect the new version.
If you are publishing a milestone or release candidate, check the pre-release checkbox.

Note that the release tags must be preceded with `v`, e.g.: `v1.2.3`.

The release workflow runs a vulnerability audit before publishing. Vulnerability findings are advisory for milestone and
release-candidate tags such as `v1.2.3-M1` and `v1.2.3-RC1`, but they remain blocking for GA releases.

Once you publish the GitHub release, the
[Release GitHub Action workflow](https://github.com/micronaut-projects/micronaut-project-template/blob/master/.github/workflows/release.yml)
will kick off, performing the following steps:

* Pre-release: sets the `projectVersion` property in `gradle.properties` to the release version, and commit and pushes
  the result.
* Builds the documentation guide, then publishes the artifacts to Maven Central. A docs build failure stops the release
  before anything is published.
* After publishing, separate jobs publish the guide to the `gh-pages` branch, generate the SLSA provenance, attach
  `artifacts.zip` to the release, and run post-release:
  * Determines the next patch version, and sets it as a `SNAPSHOT` version.
  * Closes the milestone that matches the release version, and creates a new one for the next patch.

A red release run means the release was not published to Maven Central. A job that fails after publishing leaves the
run green with a warning, and the run's summary lists the result of each step. Re-run that job on its own from the run
page; post-release is not idempotent, so check the version and milestones before re-running it.

If everything goes well, you now need to manually trigger the Maven Central publishing workflow via the GitHub UI.

If there is an issue with the release, it's important not to trigger the Maven Central publishing workflow because once
we publish a version to Maven Central we cannot change or remove it anymore.

There are some properties in `gradle.properties` that affect the release process:

* `githubBranch`: the current branch, usually `master`.
* `githubCoreBranch`: the Micronaut Core branch where the BOM update pull requests will be sent to.
* `bomProperty`: in Micronaut Core's `gradle.properties`, the property that represents this module's version.
* `bomProperties`: if needed, additional properties for the BOM pull request.

For example, assuming a module has the release `1.0.0` as the latest version published, which was included in the
Micronaut `2.2.0` BOM. If the next version you want to publish is:

* A new patch release (`1.0.1`): simply publish the existing draft release.
* A new minor release (`1.1.0`):
  * Before the release, push a `1.0.x` branch off `master`.
  * Bump the version in master to `1.1.0-SNAPSHOT`.
  * Set the `githubCoreBranch` property to `2.3.x` (or `3.0.x` if it will be the next one).
  * Edit the draft release setting the version to `1.1.0` in the release title, body, tag, etc.
  * Publish the release.
* A new major release (`2.0.0`):
  * Before the release, push a `1.0.x` branch off `master`.
  * Bump the version in master to `2.0.0-SNAPSHOT`.
  * Set the `githubCoreBranch` property to `3.0.x` (or `2.3.x` if this new major version doesn't introduce breaking changes).
  * Edit the draft release setting the version to `2.0.0` in the release title, body, tag, etc.
  * Publish the release.
