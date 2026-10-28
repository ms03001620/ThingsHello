#!/bin/sh
# Run on Pi with an already-extracted release. Service must first be installed.
set -eu
if [ "$#" -ne 2 ]; then echo 'usage: activate.sh absolute-root release-name' >&2; exit 2; fi
root=$1
release=$2
case "$root" in /*) ;; *) exit 2;; esac
case "$root" in *[!a-zA-Z0-9_./-]*) exit 2;; esac
case "$release" in ''|.|..|*[!a-zA-Z0-9_.-]*) exit 2;; esac
[ -x "$root/releases/$release/bin/car-pi" ]
(cd "$root/releases/$release" && bin/car-pi doctor && bin/car-pi verify --suite protocol --output results/preactivate-protocol.json >/dev/null && bin/car-pi verify --suite fixture-video --output results/preactivate-video.json >/dev/null)
old=$(readlink "$root/current" || true)
sudo -v
[ ! -e "$root/current.next" ] && [ ! -L "$root/current.next" ]
ln -s "$root/releases/$release" "$root/current.next"
sudo systemctl stop car-pi.service
mv -Tf "$root/current.next" "$root/current"
if sudo systemctl start car-pi.service && sleep 2 && sudo systemctl is-active --quiet car-pi.service; then
    if [ -n "$old" ]; then ln -sfn "$old" "$root/previous"; fi
else
    sudo systemctl stop car-pi.service
    if [ -n "$old" ]; then ln -s "$old" "$root/current.rollback"; mv -Tf "$root/current.rollback" "$root/current"; sudo systemctl start car-pi.service; fi
    exit 1
fi
