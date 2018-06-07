#!/bin/bash
set -e
EXIT_STATUS=0

git config --global user.name "$GIT_NAME"
git config --global user.email "$GIT_EMAIL"
git config --global credential.helper "store --file=~/.git-credentials"
echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials

if [[ $EXIT_STATUS -eq 0 ]]; then
    ./gradlew --continue http-client:check --no-daemon || EXIT_STATUS=$?
fi

./gradlew --continue test-suite:check tracing:check --no-daemon || EXIT_STATUS=$?


exit $EXIT_STATUS