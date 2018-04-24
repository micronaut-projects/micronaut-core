#!/bin/bash
set -e

EXIT_STATUS=0

./gradlew clean || EXIT_STATUS=$?

if [[ $EXIT_STATUS -ne 0 ]]; then
    echo "Clean failed"
    exit $EXIT_STATUS
fi

./gradlew check || EXIT_STATUS=$?

if [[ $EXIT_STATUS -ne 0 ]]; then
    echo "Check failed"
    exit $EXIT_STATUS
fi

# Only publish docs if the branch is on master, and it is not a PR
if [[ -n $TRAVIS_TAG ]] || [[ $TRAVIS_BRANCH == 'master' && $TRAVIS_PULL_REQUEST == 'false' ]]; then

  ./gradlew docs || EXIT_STATUS=$?

  if [[ $EXIT_STATUS -ne 0 ]]; then
      echo "Docs generation failed"
      exit $EXIT_STATUS
  fi

  git config --global user.name "$GIT_NAME"
  git config --global user.email "$GIT_EMAIL"
  git config --global credential.helper "store --file=~/.git-credentials"
  echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials

  git clone https://${GH_TOKEN}@github.com/${TRAVIS_REPO_SLUG}.git -b gh-pages gh-pages --single-branch > /dev/null
  cd gh-pages

  # If this is the master branch then update the snapshot
  if [[ $TRAVIS_BRANCH == 'master' ]]; then
    mkdir -p snapshot
    cp -r ../build/docs/. ./snapshot/
    git add snapshot/*
  fi

  # If there is a tag present then this becomes the latest
  if [[ -n $TRAVIS_TAG ]]; then
    mkdir -p latest
    cp -r ../build/docs/. ./latest/
    git add latest/*

    version="$TRAVIS_TAG" # eg: v3.0.1
    version=${version:1} # 3.0.1
    majorVersion=${version:0:4} # 3.0.
    majorVersion="${majorVersion}x" # 3.0.x

    mkdir -p "$version"
    cp -r ../build/docs/. "./$version/"
    git add "$version/*"

    mkdir -p "$majorVersion"
    cp -r ../build/docs/. "./$majorVersion/"
    git add "$majorVersion/*"
  fi

  git commit -a -m "Updating docs for Travis build: https://travis-ci.org/$TRAVIS_REPO_SLUG/builds/$TRAVIS_BUILD_ID"
  git push origin HEAD
  cd ..
  rm -rf gh-pages

fi

exit $EXIT_STATUS