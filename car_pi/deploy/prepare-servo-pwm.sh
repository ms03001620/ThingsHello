#!/bin/sh
# Root-only boot preparation. The car process remains an unprivileged gpio group user.
set -eu
p=/sys/class/pwm/pwmchip0
[ -d "$p" ] || { echo 'PWM0 missing: configure dtoverlay=pwm,pin=18,func=2' >&2; exit 1; }
pinctrl get 18 | grep -q 'GPIO18 = PWM0' || { echo 'BCM18 is not PWM0' >&2; exit 1; }
if [ ! -d "$p/pwm0" ]; then echo 0 > "$p/export"; fi
i=0
while [ ! -f "$p/pwm0/enable" ]; do
    i=$((i+1)); [ "$i" -lt 50 ]; sleep .1
done
echo 0 > "$p/pwm0/enable"
echo 0 > "$p/pwm0/duty_cycle"
echo normal > "$p/pwm0/polarity"
echo 20000000 > "$p/pwm0/period"
chgrp gpio "$p/pwm0/enable" "$p/pwm0/period" "$p/pwm0/duty_cycle"
chmod g+rw "$p/pwm0/enable" "$p/pwm0/period" "$p/pwm0/duty_cycle"
