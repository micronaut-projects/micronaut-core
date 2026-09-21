#!/usr/bin/env python3
"""Helper for .github/workflows/copilot-sonar-fix.yml.

  plan      find branches whose last commit fails the SonarCloud quality gate
            and list what fails, as the fix matrix
  prompt    render the Copilot prompt for one matrix entry
  classify  check the staged fix before it is pushed

Only the gate's failed conditions are handed to Copilot: a passing gate is not
a task, however many issues the new code period holds.
"""
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("copilot_deps", os.path.join(HERE, "copilot-deps.py"))
deps = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(deps)

PROMPT_FILE = os.path.join(deps.GITHUB_DIR, "prompts", "copilot-sonar-fix.md")
SONAR = "https://sonarcloud.io/api"
CHECK = "SonarCloud Code Analysis"
MAX_ISSUES = 100
MAX_FILES = 20

# Failed gate condition -> the issues/search filter that lists its issues
ISSUE_CONDITIONS = {
    "new_violations": {},
    "new_blocker_violations": {"severities": "BLOCKER"},
    "new_critical_violations": {"severities": "CRITICAL"},
    "new_major_violations": {"severities": "MAJOR"},
    "new_minor_violations": {"severities": "MINOR"},
    "new_bugs": {"types": "BUG"},
    "new_reliability_rating": {"types": "BUG"},
    "new_vulnerabilities": {"types": "VULNERABILITY"},
    "new_security_rating": {"types": "VULNERABILITY"},
    "new_code_smells": {"types": "CODE_SMELL"},
    "new_maintainability_rating": {"types": "CODE_SMELL"},
    "new_software_quality_blocker_issues": {"impactSeverities": "BLOCKER"},
    "new_software_quality_high_issues": {"impactSeverities": "HIGH"},
    "new_software_quality_medium_issues": {"impactSeverities": "MEDIUM"},
    "new_software_quality_reliability_rating": {"impactSoftwareQualities": "RELIABILITY"},
    "new_software_quality_security_rating": {"impactSoftwareQualities": "SECURITY"},
    "new_software_quality_maintainability_rating": {"impactSoftwareQualities": "MAINTAINABILITY"},
}
# Failed gate condition -> the per-file metric that shows where it fails
FILE_CONDITIONS = {
    "new_coverage": "new_uncovered_lines",
    "new_line_coverage": "new_uncovered_lines",
    "new_branch_coverage": "new_uncovered_conditions",
    "new_duplicated_lines_density": "new_duplicated_lines",
    "new_duplicated_lines": "new_duplicated_lines",
}


def sonar(path, **params):
    request = urllib.request.Request(f"{SONAR}/{path}?{urllib.parse.urlencode(params)}")
    token = os.environ.get("SONAR_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def failing_files(project, branch, metric):
    tree = sonar("measures/component_tree", component=project, branch=branch, metricKeys=metric,
                 qualifiers="FIL", ps=MAX_FILES, s="metricPeriod", metricSort=metric,
                 metricPeriodSort=1, asc="false")
    files = []
    for c in tree.get("components", []):
        for m in c.get("measures", []):
            value = next((p["value"] for p in m.get("periods", []) if p.get("index") == 1), "0")
            if float(value) > 0:
                files.append({"file": c["path"], "metric": metric, "value": value})
    return files


def failing_issues(project, branch, filters):
    found = sonar("issues/search", componentKeys=project, branch=branch, sinceLeakPeriod="true",
                  issueStatuses="OPEN,CONFIRMED", ps=MAX_ISSUES, additionalFields="rules", **filters)
    rules = {r["key"]: r["name"] for r in found.get("rules", [])}
    issues = [{
        "key": i["key"],
        "rule": i["rule"],
        "ruleName": rules.get(i["rule"], ""),
        "severity": i.get("severity", ""),
        "file": i["component"].split(":", 1)[-1],
        "line": i.get("line", ""),
        "message": i["message"],
    } for i in found.get("issues", [])]
    return issues, found.get("total", len(issues))


def inspect(repo, project, base):
    """The fix task for `base`, or None with the reason there is nothing to do."""
    sha = deps.gh("api", f"repos/{repo}/commits/{base}", "--jq", ".sha").strip()
    runs = json.loads(deps.gh("api", f"repos/{repo}/commits/{sha}/check-runs?check_name={urllib.parse.quote(CHECK)}",
                              "--jq", ".check_runs"))
    if not runs:
        pending = deps.gh("api", f"repos/{repo}/commits/{sha}/check-runs?per_page=100",
                          "--jq", '[.check_runs[] | select(.status != "completed")] | length').strip()
        if pending != "0":
            return None, f"CI is still running on `{sha[:10]}`; no analysis yet"
        return None, f"no `{CHECK}` check on `{sha[:10]}`"
    run = runs[0]
    if run["status"] != "completed":
        return None, f"analysis of `{sha[:10]}` is still running"
    if run["conclusion"] != "failure":
        return None, f"quality gate passes on `{sha[:10]}`"

    analysis = sonar("project_analyses/search", project=project, branch=base, ps=1)["analyses"]
    if not analysis or analysis[0].get("revision") != sha:
        return None, f"the latest SonarCloud analysis is not for `{sha[:10]}`"
    gate = sonar("qualitygates/project_status", projectKey=project, branch=base)["projectStatus"]
    if gate["status"] != "ERROR":
        return None, f"quality gate is `{gate['status']}`"

    conditions, issues, files, manual, truncated = [], {}, [], [], False
    for c in gate["conditions"]:
        if c["status"] != "ERROR":
            continue
        metric = c["metricKey"]
        conditions.append(f"{metric} is {c.get('actualValue')} "
                          f"(fails when {c['comparator']} {c['errorThreshold']})")
        if metric in ISSUE_CONDITIONS:
            found, total = failing_issues(project, base, ISSUE_CONDITIONS[metric])
            truncated |= total > len(found)
            issues.update((i["key"], i) for i in found)
        elif metric in FILE_CONDITIONS:
            files += failing_files(project, base, FILE_CONDITIONS[metric])
        else:
            # e.g. security hotspots, which a person has to review
            manual.append(metric)
    if not issues and not files:
        return None, "the failed conditions need a person: " + ", ".join(manual)

    issues = sorted(issues.values(), key=lambda i: (i["file"], i["line"] or 0))
    fingerprint = hashlib.sha256(json.dumps([sorted(i["key"] for i in issues),
                                             sorted(f["file"] for f in files)]).encode()).hexdigest()[:16]
    return {
        "base": base,
        "sha": sha,
        "head": f"copilot/sonar/{base}",
        "artifact": f"copilot-sonar-{deps.slug(base)}",
        "id": base,
        "conditions": conditions,
        "issues": issues,
        "truncated": truncated,
        "files": files,
        "manual": manual,
        "fingerprint": fingerprint,
        "dashboard": f"https://sonarcloud.io/dashboard?id={project}&branch={urllib.parse.quote(base)}",
    }, None


def earlier_pull_request(repo, head, fingerprint):
    prs = json.loads(deps.gh("pr", "list", "--repo", repo, "--head", head, "--state", "all",
                             "--json", "number,state,body", "--limit", "20"))
    marker = f"<!-- copilot-sonar fingerprint={fingerprint} -->"
    for pr in prs:
        if pr["state"] == "OPEN":
            return f"#{pr['number']} is still open"
        if pr["state"] == "CLOSED" and marker in (pr["body"] or ""):
            return f"#{pr['number']} proposed a fix for the same findings and was closed"
    return None


def plan():
    repo = os.environ["GITHUB_REPOSITORY"]
    pol = deps.policy()
    if not os.environ.get("SONAR_TOKEN"):
        sys.exit("SONAR_TOKEN is not set")
    project = pol.get("sonarProjectKey") or repo.replace("/", "_")
    branches = [b.strip() for b in os.environ.get("BASE_BRANCHES", "").split(",") if b.strip()] \
        or deps.default_branches(repo, pol["previousBranches"])

    matrix, rows = [], ["| Branch | Result |", "|---|---|"]
    for base in branches:
        task, reason = inspect(repo, project, base)
        if task:
            reason = earlier_pull_request(repo, task["head"], task["fingerprint"])
            if not reason:
                matrix.append(task)
                reason = (f"**fix**: {len(task['issues'])} issue(s), {len(task['files'])} file(s) "
                          f"for {'; '.join(task['conditions'])}")
        rows.append(f"| `{base}` | {reason} |")
    deps.summary("### SonarCloud quality gate\n\n" + "\n".join(rows))
    deps.output(matrix=json.dumps({"include": matrix}), count=len(matrix))


def prompt():
    task = json.loads(os.environ["TASK"])
    pol = deps.policy()
    issues = "\n".join(
        f"| `{i['file']}` | {i['line']} | `{i['rule']}` {i['ruleName']} | {i['severity']} | {i['message']} |"
        for i in task["issues"])
    if issues:
        issues = "| File | Line | Rule | Severity | Message |\n|---|---|---|---|---|\n" + issues
        if task["truncated"]:
            issues += "\n\nThe list is truncated; fix these and say in the report that more remain."
    files = "\n".join(f"| `{f['file']}` | {f['metric']} | {f['value']} |" for f in task["files"])
    if files:
        files = "| File | Metric | New code value |\n|---|---|---|\n" + files
    with open(PROMPT_FILE) as f:
        text = f.read()
    replacements = {
        "BASE": task["base"],
        "SHA": task["sha"],
        "CONDITIONS": "\n".join(f"- {c}" for c in task["conditions"]),
        "ISSUES": issues or "_None._",
        "FILES": files or "_None._",
        "MANUAL": ", ".join(task["manual"]) or "none",
        "VERIFY_TASKS": pol["verifyTasks"],
        "REPORT": os.environ["REPORT"],
    }
    for key, value in replacements.items():
        text = text.replace("{{" + key + "}}", value)
    sys.stdout.write(text)


def classify():
    base = os.environ["BASE_REF"]
    task = json.loads(os.environ["TASK"])
    verified = os.environ.get("VERIFY_OUTCOME") == "success"
    pol = deps.policy()

    def git(*args):
        return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout

    files = git("diff", "--cached", "--name-only", base).split()
    deleted = git("diff", "--cached", "--name-only", "--diff-filter=D", base).split()
    added = git("diff", "--cached", "-U0", base)
    forbidden = [f for f in files if any(deps.fnmatch.fnmatch(f, p) for p in pol["sonarForbiddenFiles"])]

    reasons = []
    if not files:
        status = "no-change"
    elif forbidden:
        status = "rejected"
        reasons.append("changes build or workflow files: " + ", ".join(f"`{f}`" for f in forbidden))
    elif deleted:
        status = "rejected"
        reasons.append("deletes files: " + ", ".join(f"`{f}`" for f in deleted))
    else:
        suppressions = [l for l in added.splitlines() if l.startswith("+")
                        and ("NOSONAR" in l or '@SuppressWarnings("java:S' in l or "@SuppressWarnings('" in l)]
        if suppressions:
            reasons.append("suppresses findings instead of fixing them; check that each is justified")
        if not verified:
            reasons.append("verification failed")
        status = "review" if verified else "failed"

    stat = git("diff", "--cached", "--stat", base)
    title = f"Fix SonarCloud quality gate on {task['base']}"
    deps.output(status=status, title=title, reasons="\n".join(f"- {r}" for r in reasons) or "- none",
                changes="```\n" + stat.strip() + "\n```")
    deps.summary(f"#### {task['id']}: **{status}**\n\n```\n{stat.strip()}\n```\n\n"
                 + "\n".join(f"- {r}" for r in reasons))


if __name__ == "__main__":
    {"plan": plan, "prompt": prompt, "classify": classify}[sys.argv[1]]()
