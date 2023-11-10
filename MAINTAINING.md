# Micronaut module maintenance tasks

## Triage incoming issues

New issues need to be categorised. At least with one of the following labels:

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

There are also a bunch of `relates-to` labels that can be used to further categorise issues. This is helpful in projects
with a lot of issues, or projects where different people work on different parts or modules.

The majority of the issues are defined in the
[management](https://github.com/micronaut-projects/management/blob/master/labels.tf) repo, and propagated via Terraform.
If you want new labels:

* If they can be beneficial to several repos, send a pull request to the management repo.
* If they are repo-specific, just go ahead and create them with the GitHub UI.

Finally, issues (especially bugs) should be prioritised with either `priority: high`, `priority: medium` or
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

### Automated pull requests

#### Dependency upgrades

All Micronaut repos have 2 dependency upgrade checking mechanism:

1. Renovate: it has the advantage that it performs dependency upgrades not only on build dependencies, but also on
   GitHub Actions workflows. On the other hand, its biggest downside is that it's unable to find newer versions for
   those defined in `gradle.properties`. It will also send different PRs for the same version upgrade if the artifact ID
   is different. For example, if you have `com.example:client:1.0` and `com.example:server:1.0`, and a new 1.1 version
   arrives for both, it will send 2 PRs, where they should both be upgraded at the same time.

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
template repo will get propagated automatically. The files propagated are:

* Workflow files (`.github/workflows/*`). They are copied using rsync"
  * `central-sync.yml`.
  * `dependency-update.yml`.
  * `graalvm.yml`.
  * `gradle.yml`.
  * `release.yml`.
  * `release-notes.yml`.
* Renovate configuration (`.github/renovate.json`).
* Gradle wrapper.
* `.gitignore`.
* `ISSUE_TEMPLATE.md`, `LICENSE`, `MAINTAINING.md`, `config/HEADER` and `config/spotless.license.java`.
* Checkstyle's `config/checkstyle/checkstyle.xml` and `config/checkstyle/suppressions.xml`.

Regarding the Gradle wrapper, the template repo checks every week if there is a new Gradle version. If there is, it will
upgrade the wrapper in the template repo itself, and via the files sync workflow this gets propagated to all repos. This
way we make sure we stay up-to-date regarding Gradle versions in all repos.

##### Customised workflow files

Due to limitations in the GitHub Actions design (such that they don't allow including snippets or any other kind of
reusability), for the sync'ed workflow files listed above, it is not possible to have custom steps and still be part of
the sync process, since any modification to those files will be overwritten the next time the files sync workflow is
executed.

The "Java CI" (`gradle.yml`) workflow does have the ability to have an optional setup step, though. If there is a `setup.sh`
file in the project root, it will be executed before invoking Gradle.

There are projects, such as micronaut-gcp and micronaut-kubernetes, that have made customisations to sync'ed workflows
because it's absolutely necessary. In those projects, the sync pull requests are manually merged so that the customisations
aren't lost.

Note that it is perfectly possible to have new workflows that aren't part of the sync process.

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

Once you publish the GitHub release, the
[Release GitHub Action workflow](https://github.com/micronaut-projects/micronaut-project-template/blob/master/.github/workflows/release.yml)
will kick off, performing the following steps:

* Pre-release: sets the `projectVersion` property in `gradle.properties` to the release version, and commit and pushes
  the result.
* Generates documentation guide and publishes it to the `gh-pages` branch.
* Sends a pull request to Core to update the BOM.
* Post-release:
  * Determines the next patch version, and sets it as a `SNAPSHOT` version.
  * Closes the milestone that matches the release version, and creates a new one for the next patch.

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
