#!/usr/bin/env bash

: "${VERSION?Need to set VERSION (eg 0.0.1)}"
: "${PUBLISHING_USER?Need to set PUBLISHING_USER}"
: "${PUBLISHING_PASSWORD?Need to set PUBLISHING_PASSWORD}"
: "${SIGNING_KEY_PASSPHRASE?Need to set SIGNING_KEY_PASSPHRASE}"

export SIGNING_KEY_ID="1219477C"
export PUBLISHING_URL="https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"

./gradlew clean check
./gradlew core:publishMavenJavaPublicationToMavenRepository --stacktrace
./gradlew ktor:publishMavenJavaPublicationToMavenRepository --stacktrace