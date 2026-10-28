#!/bin/sh
set -eu
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then echo 'usage: deploy.sh user@host [version]' >&2; exit 2; fi
target=$1
version=${2:-0.1.0-dev}
case "$target" in ''|-*|*[!a-zA-Z0-9_.@:-]*) echo 'invalid SSH target' >&2; exit 2;; esac
case "$version" in ''|*[!a-zA-Z0-9_.-]*) exit 2;; esac
cd "$(dirname "$0")/.."
[ -x ./gradlew ] || { echo 'Run deploy.sh from source checkout' >&2; exit 2; }
./gradlew test installDist distTar "-PreleaseVersion=$version"
archive="build/distributions/car-pi-$version.tar"
name="car-pi-$version"
ssh "$target" "mkdir -p thingshello/releases && test ! -e thingshello/releases/$name"
scp "$archive" "$target:thingshello/releases/$name.tar"
ssh "$target" "cd thingshello/releases && tar -xf $name.tar && cd $name && bin/car-pi doctor --output results/doctor.json"
