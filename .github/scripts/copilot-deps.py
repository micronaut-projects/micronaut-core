#!/usr/bin/env python3
"""Helper for .github/workflows/copilot-dependency-updates.yml.

  plan      read the Renovate Dependency Dashboard and emit the update matrix
  prompt    render the Copilot prompt for one matrix entry
  classify  classify the staged change against the branch policy

Only the standard library is used. Everything read from the dashboard is
treated as data: it is validated here and passed to Copilot as a quoted task,
never interpolated into a shell command.
"""
import fnmatch
import json
import os
import re
import subprocess
import sys

# Resolved next to this script, so a run can read the policy and prompt from
# a pristine copy that the Copilot session cannot edit.
GITHUB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
POLICY_FILE = os.path.join(GITHUB_DIR, "copilot-policy.json")
PROMPT_FILE = os.path.join(GITHUB_DIR, "prompts", "copilot-dependency-update.md")

# Dashboard sections that name an update we should make. "recreate" (ignored
# or closed by a human) is left out on purpose.
ENTRY = re.compile(
    r"^\s*- \[[ xX]\] <!-- (approve|unschedule|unlimit|retry|rebase|other)-branch=(renovate/[\w./-]+) -->(.+)$"
)
PRERELEASE = re.compile(r"(?i)[-.](alpha|beta|rc|cr|m\d+|milestone|snapshot|ea|preview|dev)")
LEADING_VERSION = re.compile(r"v?(\d+)(?:\.(\d+))?(?:\.(\d+))?")
QUOTED_VERSION = re.compile(r"""(["'])v?(\d+(?:\.[0-9A-Za-z_-]+)+)\1""")
PROPERTY_VERSION = re.compile(r"^(\s*[\w.-]+\s*=\s*)(\d+(?:\.[0-9A-Za-z_-]+)+)\s*$")
RANK = {"patch": 1, "minor": 2, "major": 3, "downgrade": 4}


def policy():
    with open(POLICY_FILE) as f:
        return json.load(f)


def gh(*args):
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout


def output(**values):
    path = os.environ.get("GITHUB_OUTPUT")
    lines = []
    for key, value in values.items():
        if "\n" in str(value):
            lines.append(f"{key}<<__EOF__\n{value}\n__EOF__")
        else:
            lines.append(f"{key}={value}")
    if path:
        with open(path, "a") as f:
            f.write("\n".join(lines) + "\n")
    else:
        print("\n".join(lines))


def summary(text):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as f:
            f.write(text + "\n")
    else:
        print(text)


# ---------------------------------------------------------------- plan

def find_dashboard(repo, pol, number):
    if number:
        issue = json.loads(gh("issue", "view", number, "--repo", repo, "--json", "number,author,body"))
    else:
        found = json.loads(gh("issue", "list", "--repo", repo, "--state", "open",
                              "--author", pol["dashboardAuthor"],
                              "--search", f'"{pol["dashboardTitle"]}" in:title',
                              "--json", "number", "--limit", "1"))
        if not found:
            sys.exit("No open Renovate Dependency Dashboard issue found")
        issue = json.loads(gh("issue", "view", str(found[0]["number"]), "--repo", repo,
                              "--json", "number,author,body"))
    author = issue["author"]["login"]
    if author not in (pol["dashboardAuthor"], pol["dashboardAuthor"].removeprefix("app/"), "renovate[bot]"):
        sys.exit(f"Issue #{issue['number']} is authored by {author}, not Renovate; refusing to read it")
    return issue


def parse_entry(action, branch, text):
    link = re.match(r"^\[(.+)\]\((.+)\)$", text.strip())
    title = link.group(1) if link else text.strip()
    packages = re.findall(r"`([^`]+)`", title)
    head = re.sub(r"\s*\((`[^`]+`(, )?)+\)\s*$", "", title)
    if not packages:
        m = re.search(r"update (?:dependency )?(\S+?)(?: action)?(?: to |$)", head)
        packages = [m.group(1)] if m else []
    target = re.search(r" to v?(\S+)$", head)
    return {
        "renovateBranch": branch,
        "section": action,
        "title": title,
        "packages": packages,
        "target": target.group(1) if target else "",
    }


def branch_mode(repo, base):
    """patch when the branch's minor is already released, otherwise minor."""
    props = gh("api", f"repos/{repo}/contents/gradle.properties?ref={base}",
               "-H", "Accept: application/vnd.github.raw")
    m = re.search(r"^projectVersion=(\d+)\.(\d+)\.(\d+)", props, re.M)
    if not m:
        sys.exit(f"Cannot read projectVersion on {base}")
    major, minor, patch = m.groups()
    if int(patch) > 0:
        return "patch", f"{major}.{minor}.{patch}"
    released = subprocess.run(["gh", "api", f"repos/{repo}/git/ref/tags/v{major}.{minor}.0"],
                              capture_output=True, text=True).returncode == 0
    return ("patch" if released else "minor"), f"{major}.{minor}.{patch}"


def default_branches(repo, previous):
    """The default branch, any newer X.Y.x branch and `previous` released lines below it."""
    default = json.loads(gh("api", f"repos/{repo}", "--jq", "{b: .default_branch}"))["b"]
    if not re.fullmatch(r"\d+\.\d+\.x", default):
        return [default]
    names = json.loads(gh("api", f"repos/{repo}/branches?per_page=100", "--jq", "[.[].name]"))
    key = lambda b: tuple(int(x) for x in re.findall(r"\d+", b))
    lines = sorted((b for b in names if re.fullmatch(r"\d+\.\d+\.x", b)), key=key)
    i = lines.index(default)
    return lines[max(0, i - previous):]


def slug(text):
    return re.sub(r"[^a-z0-9.-]+", "-", text.lower()).strip("-")


def plan():
    repo = os.environ["GITHUB_REPOSITORY"]
    pol = policy()
    issue = find_dashboard(repo, pol, os.environ.get("DASHBOARD_ISSUE", "").strip())
    only = os.environ.get("ONLY", "").strip()
    limit = int(os.environ.get("MAX_UPDATES", "").strip() or pol["maxUpdatesPerRun"])
    branches = [b.strip() for b in os.environ.get("BASE_BRANCHES", "").split(",") if b.strip()] \
        or default_branches(repo, pol["previousBranches"])

    entries, skipped = [], []
    for line in issue["body"].splitlines():
        m = ENTRY.match(line)
        if not m:
            continue
        e = parse_entry(*m.groups())
        reason = None
        if any(fnmatch.fnmatch(e["renovateBranch"], p) for p in pol["skipBranches"]):
            reason = "skipBranches"
        elif any(fnmatch.fnmatch(p, s) for p in e["packages"] for s in pol["skipPackages"]):
            reason = "skipPackages"
        elif only and not re.search(only, e["renovateBranch"] + " " + e["title"]):
            reason = "filtered"
        elif e["renovateBranch"] in {x["renovateBranch"] for x in entries}:
            reason = "duplicate"
        (skipped if reason else entries).append({**e, "skip": reason})

    modes = {base: branch_mode(repo, base) for base in branches}
    matrix = []
    table = ["| Base | Mode | Update |", "|---|---|---|"]
    # update-major order, so the limit keeps every branch of an update together
    for e in entries:
        for base in branches:
            mode, version = modes[base]
            name = e["renovateBranch"].removeprefix("renovate/")
            matrix.append({
                **{k: v for k, v in e.items() if k != "skip"},
                "base": base,
                "baseVersion": version,
                "mode": mode,
                "head": f"copilot/deps/{base}/{slug(name)}",
                "artifact": f"copilot-deps-{slug(base)}-{slug(name)}",
                "id": f"{base} {name}",
            })
            table.append(f"| `{base}` | {mode} | {e['title']} |")
    dropped = matrix[limit:]
    matrix = matrix[:limit]

    summary(f"### Dependency updates from #{issue['number']}\n")
    summary("\n".join(table[:len(matrix) + 2]))
    if dropped:
        summary(f"\n{len(dropped)} more update(s) over the limit of {limit} wait for the next run.")
    if skipped:
        summary("\n<details><summary>Skipped</summary>\n\n"
                + "\n".join(f"- {s['title']} ({s['skip']})" for s in skipped) + "\n</details>")
    output(matrix=json.dumps({"include": matrix}), count=len(matrix))


# ---------------------------------------------------------------- prompt

def prompt():
    task = json.loads(os.environ["TASK"])
    pol = policy()
    allowed = {"patch": "patch updates only (same major.minor as the current version)",
               "minor": "minor and patch updates (same major as the current version)"}[task["mode"]]
    with open(PROMPT_FILE) as f:
        text = f.read()
    replacements = {
        "BASE": task["base"],
        "BASE_VERSION": task["baseVersion"],
        "MODE": task["mode"],
        "ALLOWED": allowed,
        "TITLE": task["title"],
        "PACKAGES": ", ".join(task["packages"]) or "(see title)",
        "TARGET": task["target"] or "(not stated; find the newest allowed version)",
        "VERIFY_TASKS": pol["verifyTasks"],
        "MAVEN_VERSIONS": os.path.join(GITHUB_DIR, "scripts", "maven-versions.py"),
        "REPORT": os.environ["REPORT"],
    }
    for key, value in replacements.items():
        text = text.replace("{{" + key + "}}", value)
    sys.stdout.write(text)


# ---------------------------------------------------------------- classify

def version_type(old, new):
    a, b = LEADING_VERSION.match(old), LEADING_VERSION.match(new)
    if not a or not b:
        return "major"
    a = [int(x or 0) for x in a.groups()]
    b = [int(x or 0) for x in b.groups()]
    if b < a:
        return "downgrade"
    if a[0] != b[0] or (a[0] == 0 and a[1] != b[1]):
        return "major"
    if a[1] != b[1]:
        return "minor"
    return "patch"


def versions_in(line):
    found = [(m.start(2), m.end(2)) for m in QUOTED_VERSION.finditer(line)]
    if not found:
        m = PROPERTY_VERSION.match(line)
        if m:
            found = [(m.start(2), m.end(2))]
    key, last, values = "", 0, []
    for start, end in found:
        key += line[last:start] + "{}"
        last = end
        values.append(line[start:end])
    return (key + line[last:]).strip(), values


def version_changes(base, files):
    diff = subprocess.run(["git", "diff", "--cached", "-U0", base, "--", *files],
                          check=True, capture_output=True, text=True).stdout
    removed, added, changes, unmatched = {}, {}, [], []
    path = None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else None
        elif line.startswith(("---", "diff ", "index ", "@@")):
            continue
        elif line[:1] in "+-" and path:
            key, values = versions_in(line[1:])
            (removed if line[0] == "-" else added).setdefault((path, key), []).append((values, line[1:]))
    for k, adds in added.items():
        olds = removed.pop(k, [])
        for i, (new, text) in enumerate(adds):
            if i >= len(olds) or not new:
                unmatched.append(f"{k[0]}: {text.strip()}")
                continue
            for o, n in zip(olds[i][0], new):
                if o != n:
                    changes.append({"file": k[0], "line": text.strip(), "old": o, "new": n,
                                    "type": version_type(o, n),
                                    "prerelease": bool(PRERELEASE.search(n)) and not PRERELEASE.search(o)})
    unmatched += [f"{k[0]}: {t.strip()} (removed)" for k, olds in removed.items() for _, t in olds]
    return changes, unmatched


def classify():
    base = os.environ["BASE_REF"]
    task = json.loads(os.environ["TASK"])
    verified = os.environ.get("VERIFY_OUTCOME") == "success"
    pol = policy()

    files = subprocess.run(["git", "diff", "--cached", "--name-only", base],
                           check=True, capture_output=True, text=True).stdout.split()
    version_files = [f for f in files if any(fnmatch.fnmatch(f, p) for p in pol["versionFiles"])]
    other_files = [f for f in files if f not in version_files]
    changes, unmatched = version_changes(base, version_files) if version_files else ([], [])

    update_type = max((c["type"] for c in changes), key=RANK.get, default="none")
    reasons = []
    if not files:
        status = "no-change"
    elif any(f.startswith(".github/") for f in files):
        status = "rejected"
        reasons.append("changes files under `.github/`")
    elif not changes:
        status = "rejected"
        reasons.append("no version change was recognised in the version files")
    elif RANK[update_type] > RANK[task["mode"]]:
        status = "rejected"
        reasons.append(f"{'a downgrade' if update_type == 'downgrade' else 'a ' + update_type + ' update'} is not allowed on `{task['base']}` "
                       f"({task['mode']} updates only)")
    elif any(c["prerelease"] for c in changes):
        status = "rejected"
        reasons.append("moves to a pre-release version")
    else:
        if not verified:
            reasons.append("verification failed")
        if other_files:
            reasons.append("changes files other than version declarations: "
                           + ", ".join(f"`{f}`" for f in other_files))
        if unmatched:
            reasons.append("adds or removes declarations")
        if any(fnmatch.fnmatch(p, m) for p in task["packages"] for m in pol["manualReviewPackages"]):
            reasons.append("package is on the manual review list")
        status = "failed" if not verified else ("review" if reasons else "automerge")

    rows = ["| File | Declaration | From | To | Type |", "|---|---|---|---|---|"]
    rows += [f"| `{c['file']}` | `{c['line']}` | {c['old']} | {c['new']} | {c['type']} |" for c in changes]
    rows += [f"| {u} | | | | unrecognised |" for u in unmatched]
    report = "\n".join(rows)
    name = task["packages"][0] if task["packages"] else task["renovateBranch"].removeprefix("renovate/")
    if len(task["packages"]) > 1:
        name += f" and {len(task['packages']) - 1} more"
    title = f"Update {name} to {changes[0]['new']}" if changes else task["title"]
    output(status=status, update_type=update_type, title=title,
           reasons="\n".join(f"- {r}" for r in reasons) or "- none", changes=report)
    summary(f"#### {task['id']}: **{status}** ({update_type})\n\n{report}\n\n"
            + "\n".join(f"- {r}" for r in reasons))


if __name__ == "__main__":
    {"plan": plan, "prompt": prompt, "classify": classify}[sys.argv[1]]()
