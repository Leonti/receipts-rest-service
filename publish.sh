#!/bin/bash
set -e

version=$(date +"%y.%m.%d.%H.%M")

cp target/scala-2.11/receipts-rest-service-assembly-1.0.jar receipts-rest-service-assembly.jar

cp Dockerfile target/scala-2.11/Dockerfile
docker build -t leonti/receipts-rest-service:$version target/scala-2.11/
docker push leonti/receipts-rest-service
git tag -a v$version -m 'new version $version'

echo "Released version $version"
