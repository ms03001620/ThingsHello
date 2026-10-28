#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then echo 'usage: deploy.sh user@host' >&2; exit 2; fi
target=$1
case "$target" in -*|*[!a-zA-Z0-9_.@:-]*) echo 'invalid SSH target' >&2; exit 2;; esac
cd "$(dirname "$0")/.."
./gradlew test installDist distTar
archive=$(find build/distributions -name 'car-pi-*.tar' -type f | sort | tail -n 1)
[ -n "$archive" ]
version=$(basename "$archive" .tar)
case "$version" in *[!a-zA-Z0-9_.-]*) exit 2;; esac
ssh "$target" "mkdir -p thingshello/releases && test ! -e thingshello/releases/$version"
scp "$archive" "$target:thingshello/releases/$version.tar"
ssh "$target" "cd thingshello/releases && tar -xf $version.tar && cd $version && bin/car-pi doctor --output results/doctor.json"
