#!/bin/sh
set -eu
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then echo 'usage: deploy.sh user@host [version]' >&2; exit 2; fi
target=$1
case "$target" in ''|-*|*[!a-zA-Z0-9_.@:-]*) echo 'invalid SSH target' >&2; exit 2;; esac
cd "$(dirname "$0")/.."
[ -x ./gradlew ] || { echo 'Run deploy.sh from source checkout' >&2; exit 2; }
# 默认版本号 = 当前 git 短提交号（与 build.gradle 一致）；第 2 个参数可手动覆盖
if [ "$#" -eq 2 ]; then
    version=$2
else
    version=$(git rev-parse --short=7 HEAD)
    if [ -n "$(git status --porcelain -- .)" ]; then
        version="$version-dirty"
        echo "warning: 工作区有未提交改动，版本号为 $version；建议先提交再部署" >&2
    fi
fi
case "$version" in ''|*[!a-zA-Z0-9_.-]*) echo 'invalid version' >&2; exit 2;; esac
echo "deploy version: $version"
./gradlew test installDist distTar "-PreleaseVersion=$version"
archive="build/distributions/car-pi-$version.tar"
name="car-pi-$version"
ssh "$target" "mkdir -p thingshello/releases && test ! -e thingshello/releases/$name"
scp "$archive" "$target:thingshello/releases/$name.tar"
ssh "$target" "cd thingshello/releases && tar -xf $name.tar && cd $name && bin/car-pi doctor --output results/doctor.json"
