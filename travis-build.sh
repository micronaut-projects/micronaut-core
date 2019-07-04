#!/bin/bash
set -e
EXIT_STATUS=0

git config --global user.name "$GIT_NAME"
git config --global user.email "$GIT_EMAIL"
git config --global credential.helper "store --file=~/.git-credentials"
echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials

if [[ $EXIT_STATUS -eq 0 ]]; then
    if [[ -n $TRAVIS_TAG ]]; then
        echo "Skipping Tests to Publish Release"
        ./gradlew pTML assemble --no-daemon || EXIT_STATUS=$?
    else
        ./gradlew --stop
        ./gradlew testClasses --no-daemon || EXIT_STATUS=$?

        ./gradlew --stop
        killall -9 java
        ./gradlew check --no-daemon -x licenseTest || EXIT_STATUS=$?
    fi
fi

if [[ $EXIT_STATUS -eq 0 ]]; then
    echo "Publishing archives for branch $TRAVIS_BRANCH"
    if [[ -n $TRAVIS_TAG ]] || [[ $TRAVIS_BRANCH =~ ^1.1.x$ && $TRAVIS_PULL_REQUEST == 'false' ]]; then

      echo "Publishing archives"
      ./gradlew --stop
      if [[ -n $TRAVIS_TAG ]]; then
          ./gradlew bintrayUpload --no-daemon --stacktrace || EXIT_STATUS=$?
      else
          ./gradlew publish --no-daemon --stacktrace || EXIT_STATUS=$?
      fi

    fi
fi

exit $EXIT_STATUS
