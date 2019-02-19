#!/bin/bash
set -e
EXIT_STATUS=0

./gradlew --stop
./gradlew testClasses
./gradlew check -x test-suite:test --no-daemon -Dmicronaut.cloud.platform=OTHER || EXIT_STATUS=$?

if [[ $EXIT_STATUS -eq 0 ]]; then
    ./gradlew test-suite:test --no-daemon -Dmicronaut.cloud.platform=OTHER || EXIT_STATUS=$?
fi

exit $EXIT_STATUS