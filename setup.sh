#!/bin/bash
# The JVM, native and Python CI builds never read the local Maven repository: they resolve the
# modules of this repository as projects of the same build
case "$GITHUB_WORKFLOW" in
    "Java CI"|"Python CI"|"GraalVM Latest CI"|"GraalVM Dev CI") exit 0 ;;
esac
./gradlew pTML
