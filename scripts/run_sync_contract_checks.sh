#!/usr/bin/env bash
# Requires Kotlin 2.0+, Java 17+ and org.json:json:20240303. No Android SDK or network calls.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to the Kotlin compiler executable}"
: "${JSON_JAR:?Set JSON_JAR to org.json:json:20240303}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
"$KOTLINC" \
 app/src/main/java/com/kitsugi/animelist/model/MediaEntry.kt \
 app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt \
 app/src/main/java/com/kitsugi/animelist/data/auth/SyncSafety.kt \
 app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryBackup.kt \
 app/src/main/java/com/kitsugi/animelist/data/remote/SimklSyncContract.kt \
 scripts/tests/SyncContractChecks.kt \
 -classpath "$JSON_JAR" -include-runtime -d "$WORK/checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$WORK/checks.jar:$JSON_JAR" com.kitsugi.animelist.audit.SyncContractChecksKt
