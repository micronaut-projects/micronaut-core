#!/usr/bin/env python3
"""Helper for .github/workflows/copilot-dependency-updates.yml.

  plan      read the Renovate Dependency Dashboard and emit the update matrix
  prompt    render the Copilot prompt for one matrix entry
  classify  classify the staged change against the branch policy
  record    merge this run's outcomes into the state kept between runs

Only the standard library is used. Everything read from the dashboard is
treated as data: it is validated here and passed to Copilot as a quoted task,
never interpolated into a shell command.
"""
import datetime
import fnmatch
import hashlib
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


def fingerprint(*parts):
    return hashlib.sha256(json.dumps(parts).encode()).hexdigest()[:16]


def marker(entry, change=""):
    return f"<!-- copilot-deps entry={entry} change={change} -->"


def load_state():
    path = os.environ.get("STATE_FILE", "")
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def handled(repo, pol, state, task):
    """Why `task` needs no run now, or None."""
    now = datetime.datetime.now(datetime.timezone.utc)
    recorded = state.get(task["entry"])
    days = pol["retryAfterDays"].get(recorded["status"]) if recorded else None
    if days is not None and now - datetime.datetime.fromisoformat(recorded["at"]) < datetime.timedelta(days=days):
        return f"{recorded['status']} on {recorded['at'][:10]}; retried after {days} day(s)"
    prs = json.loads(gh("pr", "list", "--repo", repo, "--head", task["head"], "--state", "all",
                        "--json", "number,state,body", "--limit", "20"))
    for pr in prs:
        same_entry = f"entry={task['entry']} " in (pr["body"] or "")
        if pr["state"] == "OPEN" and (same_entry or not task["target"]):
            # a target-less group cannot tell a newer update from the open one
            return f"#{pr['number']} is open"
        if pr["state"] == "CLOSED" and same_entry and task["target"]:
            return f"#{pr['number']} proposed it and was closed unmerged"
    return None


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
    state = load_state()
    matrix, waiting = [], 0
    table = ["| Base | Mode | Update |", "|---|---|---|"]
    # update-major order, so each update reaches every branch in turn; handled
    # updates are filtered before the limit, so they never hold back later ones
    for e in entries:
        for base in branches:
            mode, version = modes[base]
            name = e["renovateBranch"].removeprefix("renovate/")
            task = {
                **{k: v for k, v in e.items() if k != "skip"},
                "base": base,
                "baseVersion": version,
                "mode": mode,
                "head": f"copilot/deps/{base}/{slug(name)}",
                "artifact": f"copilot-deps-{slug(base)}-{slug(name)}",
                "id": f"{base} {name}",
                "entry": fingerprint(base, e["renovateBranch"], e["title"]),
            }
            if len(matrix) >= limit:
                waiting += 1
                continue
            reason = handled(repo, pol, state, task)
            if reason:
                skipped.append({"title": f"`{base}` {e['title']}", "skip": reason})
                continue
            matrix.append(task)
            table.append(f"| `{base}` | {mode} | {e['title']} |")

    summary(f"### Dependency updates from #{issue['number']}\n")
    summary("\n".join(table))
    if waiting:
        summary(f"\n{waiting} more update(s) over the limit of {limit} wait for the next run.")
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


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def package_of(line):
    """The package a version catalog line declares, if it names one."""
    for pattern in (r'module\s*=\s*"([^":]+:[^":]+)"',
                    r'\bid\s*=\s*"([^"]+)"',
                    r'=\s*"([\w.-]+:[\w.-]+):[^"]*"'):
        m = re.search(pattern, line)
        if m:
            return m.group(1)
    m = re.search(r'group\s*=\s*"([^"]+)".*\bname\s*=\s*"([^"]+)"', line)
    return f"{m.group(1)}:{m.group(2)}" if m else None


def catalog_references(text):
    """Version key -> the packages that use it through version.ref."""
    refs = {}
    for line in text.splitlines():
        ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', line)
        package = package_of(line)
        if ref and package:
            refs.setdefault(ref.group(1), set()).add(package)
    return refs


def packages_of(change, refs):
    """The packages a changed declaration updates; empty when it cannot be told."""
    if not change["file"].endswith(".versions.toml"):
        return set()
    package = package_of(change["line"])
    if package:
        return {package}
    key = re.match(r"\s*([\w.-]+)\s*=", change["line"])
    return refs.get(key.group(1), set()) if key else set()


def version_changes(base, files):
    diff = git("diff", "--cached", "--no-renames", "-U0", base, "--", *files)
    removed, added, changes, unmatched = {}, {}, [], []
    path = old_path = None
    for line in diff.splitlines():
        if line.startswith("--- "):
            old_path = line[6:] if line.startswith("--- a/") else None
        elif line.startswith("+++ "):
            # a deleted file has no new path; its removed lines still count
            path = line[6:] if line.startswith("+++ b/") else old_path
        elif line.startswith(("diff ", "index ", "@@", "new file", "deleted file")):
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


def closed_with_same_change(task, change):
    repo = os.environ.get("GITHUB_REPOSITORY")
    if not repo or not os.environ.get("GH_TOKEN"):
        return None
    prs = json.loads(gh("pr", "list", "--repo", repo, "--head", task["head"], "--state", "closed",
                        "--json", "number,state,body", "--limit", "20"))
    return next((pr["number"] for pr in prs
                 if pr["state"] == "CLOSED" and f"change={change} " in (pr["body"] or "")), None)


def classify():
    base = os.environ["BASE_REF"]
    task = json.loads(os.environ["TASK"])
    verified = os.environ.get("VERIFY_OUTCOME") == "success"
    pol = policy()

    statuses = [line.split("\t") for line in git("diff", "--cached", "--no-renames", "--name-status", base).splitlines()]
    files = [f for _, f in statuses]
    deleted = [f for status, f in statuses if status == "D"]
    created = [f for status, f in statuses if status == "A"]
    version_files = [f for f in files if any(fnmatch.fnmatch(f, p) for p in pol["versionFiles"])]
    other_files = [f for f in files if f not in version_files]
    changes, unmatched = version_changes(base, version_files) if version_files else ([], [])

    catalog = next((f for f in version_files if f.endswith(".versions.toml") and f not in deleted), None)
    refs = catalog_references(git("show", f":{catalog}")) if catalog else {}
    changed_packages, unresolved = set(), []
    for c in changes:
        c["packages"] = sorted(packages_of(c, refs))
        changed_packages.update(c["packages"])
        if not c["packages"]:
            unresolved.append(c["line"])
    requested = set(task["packages"])
    change = fingerprint(task["base"], sorted((c["file"], c["line"], c["old"], c["new"]) for c in changes))

    update_type = max((c["type"] for c in changes), key=RANK.get, default="none")
    reasons = []
    if not files:
        status = "no-change"
    elif any(f.startswith(".github/") for f in files):
        status = "rejected"
        reasons.append("changes files under `.github/`")
    elif deleted:
        status = "rejected"
        reasons.append("deletes files: " + ", ".join(f"`{f}`" for f in deleted))
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
    elif (closed := closed_with_same_change(task, change)):
        status = "declined"
        reasons.append(f"#{closed} proposed the same change and was closed unmerged")
    else:
        if not verified:
            reasons.append("verification failed")
        if other_files:
            reasons.append("changes files other than version declarations: "
                           + ", ".join(f"`{f}`" for f in other_files))
        if created:
            reasons.append("adds files: " + ", ".join(f"`{f}`" for f in created))
        if unmatched:
            reasons.append("adds or removes declarations")
        if unresolved:
            reasons.append("cannot tell which packages these declarations update: "
                           + ", ".join(f"`{u}`" for u in unresolved))
        beyond = sorted(changed_packages - requested)
        if requested and beyond:
            reasons.append("also updates packages outside the dashboard entry: "
                           + ", ".join(f"`{p}`" for p in beyond))
        review = sorted(p for p in changed_packages | requested
                        if any(fnmatch.fnmatch(p, m) for m in pol["manualReviewPackages"]))
        if review:
            reasons.append("on the manual review list: " + ", ".join(f"`{p}`" for p in review))
        status = "failed" if not verified else ("review" if reasons else "automerge")

    rows = ["| File | Declaration | Packages | From | To | Type |", "|---|---|---|---|---|---|"]
    rows += [f"| `{c['file']}` | `{c['line']}` | {', '.join(c['packages']) or '?'} | {c['old']} | {c['new']} | {c['type']} |"
             for c in changes]
    rows += [f"| {u} | | | | | unrecognised |" for u in unmatched]
    report = "\n".join(rows)
    name = task["packages"][0] if task["packages"] else task["renovateBranch"].removeprefix("renovate/")
    if len(task["packages"]) > 1:
        name += f" and {len(task['packages']) - 1} more"
    title = f"Update {name} to {changes[0]['new']}" if changes else task["title"]
    output(status=status, update_type=update_type, title=title, marker=marker(task["entry"], change),
           reasons="\n".join(f"- {r}" for r in reasons) or "- none", changes=report)
    summary(f"#### {task['id']}: **{status}** ({update_type})\n\n{report}\n\n"
            + "\n".join(f"- {r}" for r in reasons))


# ---------------------------------------------------------------- record

def record():
    """Merge the outcome files of this run into STATE_FILE, dropping old entries."""
    state = load_state()
    directory = os.environ["OUTCOME_DIR"]
    for name in sorted(os.listdir(directory)) if os.path.isdir(directory) else []:
        if name.endswith(".json"):
            with open(os.path.join(directory, name)) as f:
                state.update(json.load(f))
    horizon = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=60)
    state = {k: v for k, v in state.items() if datetime.datetime.fromisoformat(v["at"]) > horizon}
    with open(os.environ["STATE_FILE"], "w") as f:
        json.dump(state, f, indent=1, sort_keys=True)
    summary(f"Recorded {len(state)} update outcome(s).")


def outcome():
    """Write this job's outcome for `record`."""
    task = json.loads(os.environ["TASK"])
    status = os.environ.get("STATUS") or "error"
    at = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
    os.makedirs(os.environ["OUTCOME_DIR"], exist_ok=True)
    with open(os.path.join(os.environ["OUTCOME_DIR"], f"{task['artifact']}.json"), "w") as f:
        json.dump({task["entry"]: {"status": status, "at": at, "id": task["id"]}}, f)


if __name__ == "__main__":
    {"plan": plan, "prompt": prompt, "classify": classify, "record": record, "outcome": outcome}[sys.argv[1]]()
