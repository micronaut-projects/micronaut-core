#!/usr/bin/env python3
"""List the published versions of a Maven artifact or Gradle plugin.

  maven-versions.py group:artifact [prefix]
  maven-versions.py plugin.id [prefix]

Copilot may run this instead of fetching arbitrary URLs: it only reads
maven-metadata.xml from Maven Central and the Gradle Plugin Portal.
"""
import re
import sys
import urllib.request

REPOSITORIES = ["https://repo1.maven.org/maven2", "https://plugins.gradle.org/m2"]


def main():
    if len(sys.argv) < 2 or not re.fullmatch(r"[\w.-]+(:[\w.-]+)?", sys.argv[1]):
        sys.exit(__doc__)
    coordinate = sys.argv[1]
    prefix = sys.argv[2] if len(sys.argv) > 2 else ""
    group, _, artifact = coordinate.partition(":")
    if not artifact:
        artifact = f"{group}.gradle.plugin"
    path = f"{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    for repository in REPOSITORIES:
        try:
            with urllib.request.urlopen(f"{repository}/{path}", timeout=30) as response:
                xml = response.read().decode()
        except OSError:
            continue
        versions = [v for v in re.findall(r"<version>([^<]+)</version>", xml) if v.startswith(prefix)]
        print(f"{coordinate} in {repository}:")
        print("\n".join(versions[-40:]) or "(no matching versions)")
        return
    sys.exit(f"{coordinate} was not found")


if __name__ == "__main__":
    main()
