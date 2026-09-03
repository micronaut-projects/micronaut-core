#!/usr/bin/env python3
"""Generate the Scala docs snippet parity catalog."""

from __future__ import annotations

import argparse
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DOCS_ROOT = REPO_ROOT / "src/main/docs"
SCALA_SOURCE_ROOT = REPO_ROOT / "test-suite-scala/src/test/scala"
CATALOG_PATH = REPO_ROOT / "test-suite-scala/DISABLED_TESTS.md"

SNIPPET_RE = re.compile(r"snippet::([^\[]+)\[([^\]]*)\]")
TAG_ATTR_RE = re.compile(r"\btags\s*=\s*(?:\"([^\"]*)\"|([^,\]]+))")
LANGUAGE_ATTR_RE = re.compile(r"\blanguage\s*=\s*(?:\"([^\"]*)\"|([^,\]]+))")
TAG_RE = re.compile(r"tag::([A-Za-z0-9_.-]+)\[\]")
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z_`][\w.`]*)(?:\s*;)?\s*$")
KNOWN_EXTENSIONS = {".java", ".groovy", ".kt", ".scala"}
PENDING_RUNTIME_TARGETS = {
    "io.micronaut.docs.config.itfce.EngineConfig": (
        "pending: scala-docs-061 - Scala trait @ConfigurationProperties does not attach configuration "
        "introduction advice to the nested configuration method at runtime."
    ),
    "io.micronaut.docs.config.builder.EngineConfig": (
        "pending: scala-docs-041 - @ConfigurationBuilder does not populate Scala builder properties at runtime."
    ),
    "io.micronaut.docs.config.builder.EngineFactory": (
        "pending: scala-docs-042 - @ConfigurationBuilder does not populate Scala builder properties at runtime."
    ),
    "io.micronaut.docs.config.builder.VehicleSpec": (
        "pending: scala-docs-043 - @ConfigurationBuilder does not populate Scala builder properties at runtime."
    ),
    "io.micronaut.docs.http.server.netty.websocket.ChatClientWebSocket": (
        "pending: scala-docs-219 - Scala @ClientWebSocket introductions fail at runtime with a missing "
        "introduction interceptor for the generated setWebSocketSession method."
    ),
    "io.micronaut.docs.http.server.netty.websocket.ChatServerWebSocket": (
        "pending: scala-docs-220 - WebSocket behavior depends on Scala @ClientWebSocket introductions, "
        "which fail at runtime with a missing setWebSocketSession introduction interceptor."
    ),
    "io.micronaut.docs.http.server.netty.websocket.ReactivePojoChatServerWebSocket": (
        "pending: scala-docs-221 - WebSocket behavior depends on Scala @ClientWebSocket introductions, "
        "which fail at runtime with a missing setWebSocketSession introduction interceptor."
    ),
}
PENDING_COMPILE_TARGETS = {
    "io.micronaut.docs.expressions.AnnotationContextExample": (
        "pending: scala-docs-060 - Scala annotation constructor members do not expose member-scoped "
        "@AnnotationExpressionContext metadata; compile fails with the member context method unavailable."
    ),
}
UNSUPPORTED_TARGETS = {
    "io.micronaut.docs.ioc.beans.User": (
        "Scala has no direct equivalent for the Java public-field accessKind example; Scala fields compile "
        "to private JVM fields with accessor methods."
    ),
}
OWNING_TEST_OVERRIDES = {
    "io.micronaut.docs.config.env.DataSourceConfiguration": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/config/env/EachPropertyTest.scala"
    ),
    "io.micronaut.docs.config.env.RateLimitsConfiguration": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/config/env/EachPropertyTest.scala"
    ),
    "io.micronaut.docs.config.env.RateLimitsFactory": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/config/env/OrderTest.scala"
    ),
    "io.micronaut.docs.ioc.mappers.ContactForm": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/ioc/mappers/SimpleMapperSpec.scala"
    ),
    "io.micronaut.docs.ioc.mappers.ContactEntity": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/ioc/mappers/SimpleMapperSpec.scala"
    ),
    "io.micronaut.docs.ioc.mappers.ContactMappers": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/ioc/mappers/SimpleMapperSpec.scala"
    ),
    "io.micronaut.docs.context.events.SampleEvent": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/context/events/application/SampleEventListenerSpec.scala"
    ),
    "io.micronaut.docs.context.events.SampleEventEmitterBean": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/context/events/application/SampleEventListenerSpec.scala"
    ),
    "io.micronaut.docs.http.server.bind.annotation.ShoppingCart": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/http/server/bind/ShoppingCartControllerTest.scala"
    ),
    "io.micronaut.docs.http.server.bind.annotation.ShoppingCartRequestArgumentBinder": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/http/server/bind/ShoppingCartControllerTest.scala"
    ),
    "io.micronaut.docs.http.server.bind.annotation.ShoppingCartController": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/http/server/bind/ShoppingCartControllerTest.scala"
    ),
    "io.micronaut.docs.http.server.bind.type.ShoppingCart": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/http/server/bind/ShoppingCartControllerTest.scala"
    ),
    "io.micronaut.docs.http.server.bind.type.ShoppingCartRequestArgumentBinder": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/http/server/bind/ShoppingCartControllerTest.scala"
    ),
    "io.micronaut.docs.http.server.bind.type.ShoppingCartController": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/http/server/bind/ShoppingCartControllerTest.scala"
    ),
    "io.micronaut.docs.server.routing.RouteConditionController": (
        "test-suite-scala/src/test/scala/io/micronaut/docs/server/routing/RouteConditionControllerSpec.scala"
    ),
}


@dataclass(frozen=True)
class SourceFile:
    path: Path
    language: str
    tags: frozenset[str]
    text: str


@dataclass(frozen=True)
class SnippetTarget:
    source: Path
    line: int
    target: str
    tags: tuple[str, ...]
    language: str | None
    id: str


def repo_path(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def parse_attr(regex: re.Pattern[str], attributes: str) -> str | None:
    match = regex.search(attributes)
    if not match:
        return None
    return (match.group(1) or match.group(2) or "").strip()


def parse_tags(attributes: str) -> tuple[str, ...]:
    raw = parse_attr(TAG_ATTR_RE, attributes)
    if raw is None:
        return tuple()
    return tuple(tag.strip() for tag in raw.split(",") if tag.strip())


def normalize_target(target: str) -> str:
    target = target.strip()
    for extension in KNOWN_EXTENSIONS:
        if target.endswith(extension):
            return target[: -len(extension)]
    return target


def expected_scala_path(target: str) -> Path:
    normalized = normalize_target(target)
    return SCALA_SOURCE_ROOT / (normalized.replace(".", "/") + ".scala")


def read_package(lines: list[str]) -> str | None:
    for line in lines[:80]:
        match = PACKAGE_RE.match(line)
        if match:
            return match.group(1).replace("`", "")
    return None


def source_language(path: Path) -> str:
    if path.suffix == ".java":
        return "java"
    if path.suffix == ".groovy":
        return "groovy"
    if path.suffix == ".kt":
        return "kotlin"
    if path.suffix == ".scala":
        return "scala"
    return path.suffix.lstrip(".")


def index_sources() -> dict[str, list[SourceFile]]:
    sources: dict[str, list[SourceFile]] = defaultdict(list)
    roots = [
        REPO_ROOT / "inject-groovy/src/test/groovy",
        REPO_ROOT / "inject-java/src/test/java",
        REPO_ROOT / "test-suite/src/test/groovy",
        REPO_ROOT / "test-suite/src/test/java",
        REPO_ROOT / "test-suite-groovy/src/test/groovy",
        REPO_ROOT / "test-suite-kotlin/src/test/kotlin",
        SCALA_SOURCE_ROOT,
        REPO_ROOT / "http-client/src/test/groovy",
        REPO_ROOT / "http-server-netty/src/test/groovy",
        REPO_ROOT / "validation/src/test/groovy",
    ]
    for root in roots:
        if not root.exists():
            continue
        for path in sorted(root.rglob("*")):
            if path.suffix not in KNOWN_EXTENSIONS:
                continue
            text = path.read_text(encoding="utf-8")
            package_name = read_package(text.splitlines())
            if not package_name:
                continue
            stem = "package-info" if path.name.startswith("package-info.") else path.stem
            fqcn = f"{package_name}.{stem}"
            sources[fqcn].append(
                SourceFile(
                    path=path,
                    language=source_language(path),
                    tags=frozenset(TAG_RE.findall(text)),
                    text=text,
                )
            )
    return sources


def collect_snippet_targets() -> list[SnippetTarget]:
    targets: list[SnippetTarget] = []
    sequence = 0
    for adoc in sorted(DOCS_ROOT.rglob("*.adoc")):
        for line_no, line in enumerate(adoc.read_text(encoding="utf-8").splitlines(), start=1):
            for match in SNIPPET_RE.finditer(line):
                target_part = match.group(1)
                attributes = match.group(2)
                tags = parse_tags(attributes)
                language = parse_attr(LANGUAGE_ATTR_RE, attributes)
                for target in [item.strip() for item in target_part.split(",") if item.strip()]:
                    sequence += 1
                    targets.append(
                        SnippetTarget(
                            source=adoc,
                            line=line_no,
                            target=target,
                            tags=tags,
                            language=language,
                            id=f"scala-docs-{sequence:03d}",
                        )
                    )
    return targets


def classify(ref: SnippetTarget, sources: dict[str, list[SourceFile]]) -> tuple[str, str, str, str]:
    normalized = normalize_target(ref.target)
    scala_path = expected_scala_path(ref.target)
    scala_source = next((source for source in sources.get(normalized, []) if source.language == "scala"), None)
    source_languages = ", ".join(sorted({source.language for source in sources.get(normalized, [])})) or "-"

    if ref.language == "scala":
        if not scala_source:
            return "missing", "Scala-specific docs snippet has no Scala source.", repo_path(scala_path), source_languages
        missing_tags = [tag for tag in ref.tags if tag not in scala_source.tags]
        if missing_tags:
            return "missing", f"Scala-specific source is missing tags: {', '.join(missing_tags)}.", repo_path(scala_path), source_languages
        return "scala-specific", "Scala-specific docs snippet.", repo_path(scala_path), source_languages

    if ref.source.as_posix().find("/languageSupport/kotlin/") >= 0:
        return "unsupported", "Kotlin language-support example.", "n/a", source_languages

    if ref.target.endswith(".kt") or ".server.suspend." in ref.target:
        return "unsupported", "Kotlin suspend/coroutine example.", "n/a", source_languages

    if normalized.endswith(".package-info"):
        return "unsupported", "Scala has no package-info source equivalent.", "n/a", source_languages

    if normalized in UNSUPPORTED_TARGETS:
        return "unsupported", UNSUPPORTED_TARGETS[normalized], "n/a", source_languages

    source_text = "\n".join(source.text for source in sources.get(normalized, []))
    if re.search(r"\brecord\s+" + re.escape(normalized.rsplit(".", 1)[-1]) + r"\b", source_text):
        return "unsupported", "Java record example without a direct Scala source equivalent.", "n/a", source_languages

    if normalized in PENDING_COMPILE_TARGETS:
        return "pending-compile", PENDING_COMPILE_TARGETS[normalized], repo_path(scala_path), source_languages

    if not scala_source:
        return "missing", "No Scala source found for this portable docs target.", repo_path(scala_path), source_languages

    missing_tags = [tag for tag in ref.tags if tag not in scala_source.tags]
    if missing_tags:
        return "missing", f"Existing Scala source is missing tags: {', '.join(missing_tags)}.", repo_path(scala_path), source_languages

    if normalized in PENDING_RUNTIME_TARGETS:
        return "pending-runtime", PENDING_RUNTIME_TARGETS[normalized], repo_path(scala_path), source_languages

    return "covered", "Scala source and referenced tags are present.", repo_path(scala_path), source_languages


def owning_test(ref: SnippetTarget, sources: dict[str, list[SourceFile]]) -> str:
    normalized = normalize_target(ref.target)
    if normalized in OWNING_TEST_OVERRIDES:
        return OWNING_TEST_OVERRIDES[normalized]

    simple_name = normalized.rsplit(".", 1)[-1]
    package_name = normalized.rsplit(".", 1)[0] if "." in normalized else ""

    if simple_name.endswith(("Spec", "Test")):
        path = expected_scala_path(ref.target)
        return repo_path(path) if path.exists() else "TBD"

    package_path = SCALA_SOURCE_ROOT / package_name.replace(".", "/")
    if not package_path.exists():
        return "TBD"

    specs = sorted(
        [
            path
            for path in package_path.glob("*Spec.scala")
            if f"{package_name}.{path.stem}" in sources
        ]
        + [
            path
            for path in package_path.glob("*Test.scala")
            if f"{package_name}.{path.stem}" in sources
        ]
    )
    return repo_path(specs[0]) if specs else "TBD"


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def render_catalog(targets: list[SnippetTarget], sources: dict[str, list[SourceFile]]) -> str:
    rows: list[tuple[SnippetTarget, str, str, str, str, str]] = []
    counts = Counter()
    missing_files = 0
    missing_tags = 0

    for ref in targets:
        status, reason, expected_path, source_languages = classify(ref, sources)
        test_path = owning_test(ref, sources)
        if status == "unsupported" and test_path == "TBD":
            test_path = "n/a"
        rows.append((ref, status, expected_path, test_path, reason, source_languages))
        counts[status] += 1
        if status == "missing" and reason.startswith("No Scala source"):
            missing_files += 1
        if status == "missing" and "missing tags" in reason:
            missing_tags += 1

    unique_targets = {normalize_target(ref.target) for ref in targets}
    missing_tag_inclusions = sum(1 for ref, status, _, _, reason, _ in rows if status == "missing" and "missing tags" in reason)
    existing_scala_inclusions = (
        counts["covered"]
        + counts["scala-specific"]
        + counts["pending-runtime"]
        + missing_tag_inclusions
    )
    complete_scala_inclusions = counts["covered"] + counts["scala-specific"] + counts["pending-runtime"]

    lines = [
        "# Scala Docs Snippet Parity Catalog",
        "",
        "This file is the durable catalog/backlog for Scala documentation snippet parity.",
        "Regenerate it with `test-suite-scala/scripts/scala_docs_snippet_catalog.py --write` after docs or Scala snippet changes.",
        "",
        "## Scope",
        "",
        f"- Snippet references: {sum(1 for _ in DOCS_ROOT.rglob('*.adoc') for line in _.read_text(encoding='utf-8').splitlines() if 'snippet::' in line)}",
        f"- Expanded target inclusions: {len(targets)}",
        f"- Unique expanded targets: {len(unique_targets)}",
        f"- Existing Scala target inclusions: {existing_scala_inclusions}",
        f"- Existing Scala target inclusions with complete tags: {complete_scala_inclusions}",
        f"- Missing Scala source inclusions: {missing_files}",
        f"- Existing Scala inclusions with missing tags: {missing_tags}",
        "",
        "## Statuses",
        "",
        "- `covered`: Scala source exists, referenced tags exist, and the owning test is present when known.",
        "- `missing`: Portable target has no Scala source yet, or an existing Scala source is missing referenced tags.",
        "- `pending-runtime`: Scala source compiles, but the behavior test is disabled because runtime support is not ready.",
        "- `pending-compile`: Portable target cannot be checked into the compiled Scala source set yet.",
        "- `unsupported`: Source is intentionally not portable to Scala.",
        "- `scala-specific`: Scala-only companion coverage that is not a direct port of a docs snippet.",
        "",
        "## Summary By Status",
        "",
        "| Status | Count |",
        "| --- | ---: |",
    ]
    for status in ["covered", "missing", "pending-runtime", "pending-compile", "unsupported", "scala-specific"]:
        lines.append(f"| `{status}` | {counts[status]} |")

    lines.extend(
        [
            "",
            "## Catalog",
            "",
            "| ID | Status | Source | Target | Tags | Expected Scala Path | Owning Test | Source Languages | Reason |",
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
        ]
    )

    for ref, status, expected_path, test_path, reason, source_languages in rows:
        tags = ", ".join(ref.tags) if ref.tags else "-"
        source = f"{repo_path(ref.source)}:{ref.line}"
        lines.append(
            "| "
            + " | ".join(
                [
                    ref.id,
                    f"`{status}`",
                    escape_cell(source),
                    f"`{escape_cell(ref.target)}`",
                    escape_cell(tags),
                    f"`{escape_cell(expected_path)}`" if expected_path != "n/a" else "n/a",
                    f"`{escape_cell(test_path)}`" if test_path != "TBD" else "TBD",
                    escape_cell(source_languages),
                    escape_cell(reason),
                ]
            )
            + " |"
        )

    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- Supporting snippet-only files must compile even when they do not own a behavior test.",
            "- Runtime failures should be represented by JUnit 5 `@Disabled(\"pending: <catalog id/reason>\")` and a matching `pending-runtime` entry here.",
            "- Compile blockers should remain outside the compiled Scala source set and be tracked as `pending-compile` entries.",
            "- Non-portable snippets are cataloged as `unsupported` instead of being dropped.",
            "",
        ]
    )
    return "\n".join(lines)


def validate(targets: list[SnippetTarget]) -> None:
    seen = set()
    for ref in targets:
        key = ref.id
        if key in seen:
            raise RuntimeError(f"Duplicate catalog id: {key}")
        seen.add(key)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help=f"write {repo_path(CATALOG_PATH)}")
    parser.add_argument("--check", action="store_true", help="verify the checked-in catalog is current")
    args = parser.parse_args()

    sources = index_sources()
    targets = collect_snippet_targets()
    validate(targets)
    markdown = render_catalog(targets, sources)
    if args.write:
        CATALOG_PATH.write_text(markdown, encoding="utf-8")
    elif args.check:
        current = CATALOG_PATH.read_text(encoding="utf-8")
        if current != markdown:
            raise RuntimeError(f"{repo_path(CATALOG_PATH)} is not current; rerun with --write")
        catalog_entries = sum(1 for line in current.splitlines() if line.startswith("| scala-docs-"))
        if catalog_entries != len(targets):
            raise RuntimeError(
                f"catalog entry count {catalog_entries} does not match expanded target count {len(targets)}"
            )
        print(
            f"OK: {catalog_entries} catalog entries cover {len(targets)} expanded snippet targets."
        )
    else:
        print(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
