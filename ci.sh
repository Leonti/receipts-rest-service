#!/bin/bash
set -e

version=$(date +"%y.%m.%d.%H.%M")
export VERSION=$version

docker login -e="$DOCKER_EMAIL" -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"

rm -rf /tmp/receipts-target
rm -rf /tmp/receipts-target-tests
mkdir -p /tmp/receipts-target
mkdir -p /tmp/receipts-target-tests

export AUTH_TOKEN_SECRET="anything"
export USE_OCR_STUB=true

docker-compose down

echo "==== Unit tests ===="
docker-compose run test
echo "==== Assembly ===="
docker-compose run assembly
echo "==== Build docker image ===="
docker-compose build app

echo "==== Integration tests ===="
set +e
docker-compose run integration-tests
if [ $? -ne 0 ]
then
  echo "Integrtion tests failed"
  docker-compose logs app
  exit 1
fi
set -e

echo "==== Push docker image to repository ===="
docker-compose push app

git tag -a v$version -m 'new version $version'

git push --quiet "https://${TAG_TOKEN}@github.com/Leonti/receipts-rest-service" HEAD:master --follow-tags > /dev/null 2>&1

echo "Released version $version"
