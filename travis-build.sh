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
        ./gradlew pTML assemble || EXIT_STATUS=$?
    else
        ./gradlew --stop
        ./gradlew testClasses || EXIT_STATUS=$?

        ./gradlew check --no-daemon || EXIT_STATUS=$?
    fi

      if [[ -n $TRAVIS_TAG ]]; then
        echo "set released version in static website"
        git clone https://${GH_TOKEN}@github.com/micronaut-projects/static-website.git -b master static-website-master --single-branch > /dev/null
        cd static-website-master
        version="$TRAVIS_TAG"
        version=${version:1}
        ./release.sh $version
        git commit -a -m "Updating micronaut version at static website for Travis build: https://travis-ci.org/$TRAVIS_REPO_SLUG/builds/$TRAVIS_BUILD_ID" && {
          git push origin HEAD || true
        }
        cd ..
        rm -r static-website-master
      fi
fi

exit $EXIT_STATUS