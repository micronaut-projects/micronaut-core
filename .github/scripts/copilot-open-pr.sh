#!/usr/bin/env bash
# Commits the staged change, pushes it to $HEAD and opens or updates the pull
# request into $BASE. Shared by the Copilot workflows; runs on the clean
# publish runner, never where Copilot ran.
#
#   GH_TOKEN       token that pushes and opens pull requests
#   BASE, HEAD     base branch and the branch to push
#   TITLE          commit and pull request title
#   COMMIT_BODY    second paragraph of the commit message
#   BODY_FILE      pull request body
#   STATUS         automerge (review, then merge once checks pass), review or failed (draft)
#   LABEL_PREFIX   e.g. "copilot-deps"; the label is "$LABEL_PREFIX: $STATUS"
#   EXTRA_LABEL    optional label added on creation
#   COMMIT_EMAIL   commit author email
set -euo pipefail

git config user.name "micronaut-build"
git config user.email "$COMMIT_EMAIL"
git commit -q -m "$TITLE" -m "$COMMIT_BODY"
git push --force "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "HEAD:refs/heads/$HEAD"

labels=("automerge:0e8a16" "review:fbca04" "failed:d93f0b")
for entry in "${labels[@]}"; do
  gh label create "$LABEL_PREFIX: ${entry%%:*}" --color "${entry##*:}" --repo "$GITHUB_REPOSITORY" 2>/dev/null || true
done
label="$LABEL_PREFIX: $STATUS"

number=$(gh pr list --repo "$GITHUB_REPOSITORY" --head "$HEAD" --state open --json number --jq '.[0].number // ""')
if [ -n "$number" ]; then
  gh pr edit "$number" --repo "$GITHUB_REPOSITORY" --title "$TITLE" --body-file "$BODY_FILE" \
    --remove-label "$LABEL_PREFIX: automerge,$LABEL_PREFIX: review,$LABEL_PREFIX: failed" --add-label "$label"
  if [ "$STATUS" = failed ]; then
    gh pr ready "$number" --repo "$GITHUB_REPOSITORY" --undo || true
  else
    gh pr ready "$number" --repo "$GITHUB_REPOSITORY" || true
  fi
else
  args=(--repo "$GITHUB_REPOSITORY" --base "$BASE" --head "$HEAD" --title "$TITLE" --body-file "$BODY_FILE" --label "$label")
  [ -n "${EXTRA_LABEL:-}" ] && args+=(--label "$EXTRA_LABEL")
  [ "$STATUS" = failed ] && args+=(--draft)
  url=$(gh pr create "${args[@]}")
  number=${url##*/}
fi

if [ "$STATUS" = automerge ]; then
  # waits for the branch's required status checks
  gh pr merge "$number" --repo "$GITHUB_REPOSITORY" --auto --squash \
    || gh pr comment "$number" --repo "$GITHUB_REPOSITORY" \
         --body "The policy allows auto-merge, but it could not be enabled. Is \"Allow auto-merge\" on for this repository?"
else
  gh pr merge "$number" --repo "$GITHUB_REPOSITORY" --disable-auto 2>/dev/null || true
fi
echo "Pull request: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/pull/$number" >> "$GITHUB_STEP_SUMMARY"
