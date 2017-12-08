#!/bin/bash
set -e
export VERSION=1

mkdir -p /tmp/receipts-target
mkdir -p /tmp/receipts-target-tests

export AUTH_TOKEN_SECRET="anything"
export USE_OCR_STUB=true

docker-compose down
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

echo "Integration tests passed succesfully!"
