#!/bin/bash
set -e
EXIT_STATUS=0

git config --global user.name "$GIT_NAME"
git config --global user.email "$GIT_EMAIL"
git config --global credential.helper "store --file=~/.git-credentials"
echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials

./gradlew sdkMinorRelease

exit $EXIT_STATUS
