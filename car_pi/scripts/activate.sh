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
# 预检用线上服务同一份配置，并在新版本目录下执行（与 systemd 的 WorkingDirectory=current 一致）。
config="$root/config/service.properties"
[ -f "$config" ] || { echo "missing $config" >&2; exit 1; }
(cd "$root/releases/$release" && bin/car-pi doctor --config "$config" --output results/preactivate-doctor.json >/dev/null)
old=$(readlink "$root/current" || true)
sudo -v
[ ! -e "$root/current.next" ] && [ ! -L "$root/current.next" ]
ln -s "$root/releases/$release" "$root/current.next"
# 新服务就绪：日志出现 READY 行，且其中的 TCP 端口处于监听状态（最多等 15 秒）。
ready() {
    i=0
    while [ "$i" -lt 15 ]; do
        sleep 1
        i=$((i + 1))
        sudo systemctl is-active --quiet car-pi.service || return 1
        port=$(sudo journalctl -u car-pi.service --since "@$started" -o cat 2>/dev/null | sed -n 's/^READY tcp=\([0-9][0-9]*\).*/\1/p' | tail -n 1)
        if [ -n "$port" ] && ss -ltnH "sport = :$port" | grep -q .; then return 0; fi
    done
    return 1
}
sudo systemctl stop car-pi.service
started=$(date +%s)
mv -Tf "$root/current.next" "$root/current"
if sudo systemctl start car-pi.service && ready; then
    if [ -n "$old" ]; then ln -sfn "$old" "$root/previous"; fi
else
    sudo systemctl stop car-pi.service
    if [ -n "$old" ]; then ln -s "$old" "$root/current.rollback"; mv -Tf "$root/current.rollback" "$root/current"; sudo systemctl start car-pi.service; fi
    exit 1
fi
