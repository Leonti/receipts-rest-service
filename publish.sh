#!/bin/bash
set -e

version=$(date +"%y.%m.%d.%H.%M")

cp Dockerfile target/scala-2.11/Dockerfile
docker login -e="$DOCKER_EMAIL" -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
docker build -t leonti/receipts-rest-service:$version target/scala-2.11/
docker push leonti/receipts-rest-service
git tag -a v$version -m 'new version $version'

git push --quiet "https://${TAG_TOKEN}@github.com/Leonti/receipts-rest-service" HEAD:master --follow-tags > /dev/null 2>&1


echo "Released version $version"
