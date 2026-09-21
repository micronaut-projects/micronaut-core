#!/bin/bash
# The Python CI workflow only runs the Python tests, which need nothing published to the local Maven repository
[ "$GITHUB_WORKFLOW" = "Python CI" ] && exit 0
./gradlew pTML
