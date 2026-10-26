#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  cp .env.example .env
  echo 'Created .env. Fill Google settings following README.md, then run again.'
  exit 1
fi
if [ -f seminar-app.jar ]; then exec java -jar seminar-app.jar; fi
if [ -f target/seminar-app.jar ]; then exec java -jar target/seminar-app.jar; fi
echo 'Build first: ./mvnw clean package'
exit 1
