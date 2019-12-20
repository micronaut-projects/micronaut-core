#!/bin/bash
set -e
EXIT_STATUS=0

git config --global user.name "$GIT_NAME"
git config --global user.email "$GIT_EMAIL"
git config --global credential.helper "store --file=~/.git-credentials"
echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials

if [[ -n $TRAVIS_TAG ]]; then
    ./gradlew pTML assemble --no-daemon || EXIT_STATUS=$?
    if [[ $EXIT_STATUS -eq 0 ]]; then
        echo "Publishing archives"
        ./gradlew bintrayUpload --no-daemon --stacktrace || EXIT_STATUS=$?
        if [[ $EXIT_STATUS -eq 0 ]]; then
            ./gradlew bintrayPublish --no-daemon --stacktrace || EXIT_STATUS=$?

            if [[ $EXIT_STATUS -eq 0 ]]; then
                ./gradlew --console=plain --no-daemon docs  || EXIT_STATUS=$?

                git clone https://${GH_TOKEN}@github.com/micronaut-projects/micronaut-docs.git -b gh-pages gh-pages --single-branch > /dev/null

                cd gh-pages
                #      mkdir -p latest
                #      cp -r ../build/docs/. ./latest/
                #      git add latest/*

                version="$TRAVIS_TAG"
                version=${version:1}
                majorVersion=${version:0:4}
                majorVersion="${majorVersion}x"

                mkdir -p "$version"
                cp -r ../build/docs/. "./$version/"
                git add "$version/*"

                mkdir -p "$majorVersion"
                cp -r ../build/docs/. "./$majorVersion/"
                git add "$majorVersion/*"

                git commit -a -m "Updating docs for Travis build: https://travis-ci.org/$TRAVIS_REPO_SLUG/builds/$TRAVIS_BUILD_ID" && {
                    git push origin HEAD || true
                }
                cd ..

                rm -rf gh-pages

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
                rm -rf static-website-master
            fi
        fi
    fi
fi

exit $EXIT_STATUS
