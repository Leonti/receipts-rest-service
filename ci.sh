#!/bin/bash
set -e

version=$(date +"%y.%m.%d.%H.%M")
export VERSION=$version

docker login -e="$DOCKER_EMAIL" -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"

mkdir /tmp/receipts-target
mkdir /tmp/receipts-target-tests

export AUTH_TOKEN_SECRET="anything"
export USE_OCR_STUB=true

docker-compose down
docker-compose run test
docker-compose run assembly
docker-compose build app
docker-compose run integration-tests
docker-compose push app

git tag -a v$version -m 'new version $version'

git push --quiet "https://${TAG_TOKEN}@github.com/Leonti/receipts-rest-service" HEAD:master --follow-tags > /dev/null 2>&1

echo "Released version $version"
