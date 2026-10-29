# GPIO worker: PWM timing comes from native liblgpio, not this Python loop.
import json, os, signal, sys, time
from pathlib import Path
import lgpio

inactive = {int(pin): int(value) for pin, value in json.loads(sys.argv[1]).items()}
handle = lgpio.gpiochip_open(0)
claimed, frequencies = set(), {}
servo_pin = None if len(sys.argv) < 3 or sys.argv[2] == "none" else int(sys.argv[2])
pwm_path = Path(os.environ.get("CAR_PI_PWM_PATH", "/sys/class/pwm/pwmchip0/pwm0"))

def pwm_write(name, value):
    (pwm_path / name).write_text(str(value))

def servo(value):
    if value != 0 and not 800 <= value <= 1500:
        raise ValueError("Servo pulse outside 800..1500 us")
    if value == 0:
        pwm_write("enable", 0)
    else:
        pwm_write("duty_cycle", value * 1000)
        pwm_write("enable", 1)


def checked(value):
    if value < 0:
        raise RuntimeError("lgpio status %d" % value)
    return value

def claim(pin):
    if pin not in inactive:
        raise ValueError("GPIO not enabled in configuration: %d" % pin)
    if pin not in claimed:
        bias = lgpio.SET_PULL_UP if inactive[pin] else lgpio.SET_PULL_DOWN
        checked(lgpio.gpio_claim_output(handle, pin, inactive[pin], bias))
        claimed.add(pin)

def stop_pwm(pin):
    if pin in claimed:
        if checked(lgpio.tx_busy(handle, pin, lgpio.TX_PWM)):
            checked(lgpio.tx_pulse(handle, pin, 0, 0))
        deadline = time.monotonic() + .2
        while checked(lgpio.tx_busy(handle, pin, lgpio.TX_PWM)):
            if time.monotonic() >= deadline:
                raise RuntimeError("PWM stop timed out")
            time.sleep(.001)
        checked(lgpio.gpio_write(handle, pin, inactive[pin]))

def free(pin):
    if pin in claimed:
        stop_pwm(pin)
        checked(lgpio.gpio_free(handle, pin))
        claimed.remove(pin)

def cleanup():
    errors = []
    if servo_pin is not None:
        try:
            servo(0)
        except Exception as error:
            errors.append(str(error))
    for pin in list(claimed):
        try:
            free(pin)
        except Exception as error:
            errors.append(str(error))
    lgpio.gpiochip_close(handle)
    return errors

def interrupt(signum, frame):
    raise SystemExit(128 + signum)

signal.signal(signal.SIGTERM, interrupt)
signal.signal(signal.SIGINT, interrupt)
ready = False
try:
    if servo_pin is not None:
        if servo_pin != 18:
            raise ValueError("Hardware PWM0 requires BCM18")
        servo(0)
        pwm_write("duty_cycle", 0)
        pwm_write("period", 20000000)
    ready = True
    print(json.dumps({"ok": True, "value": 0}), flush=True)
    for line in sys.stdin:
        try:
            request = json.loads(line)
            op = request["op"]
            if op == "close":
                break
            pin, value = int(request["pin"]), int(request["value"])
            if servo_pin is not None and pin == servo_pin:
                if op == "servo":
                    servo(value)
                elif op == "mode" and value == 0:
                    servo(0)  # Keep ALT5 pin mux; never claim PWM0 through lgpio.
                else:
                    raise ValueError("Servo pin is reserved for hardware PWM")
                print(json.dumps({"ok": True, "value": 0}), flush=True)
                continue
            if pin not in inactive:
                raise ValueError("GPIO not configured")
            result = 0
            if op == "mode":
                if value == 0:
                    free(pin)
                elif value == 1:
                    claim(pin)
                else:
                    raise ValueError("Only input/release and output supported")
            elif op == "frequency":
                if not 1 <= value <= 10000:
                    raise ValueError("PWM frequency out of range")
                frequencies[pin] = value
                result = value  # configured value; not a measured waveform
            elif op == "range":
                if value != 100:
                    raise ValueError("Duty range must be 100")
            elif op == "write":
                if value not in (0, 1):
                    raise ValueError("Invalid digital level")
                claim(pin)
                checked(lgpio.gpio_write(handle, pin, value))
            elif op == "pwm":
                if not 0 <= value <= 100:
                    raise ValueError("Invalid duty cycle")
                claim(pin)
                if value == 0:
                    stop_pwm(pin)
                else:
                    checked(lgpio.tx_pwm(handle, pin, frequencies.get(pin, 300), value))
            else:
                raise ValueError("Unsupported operation")
            print(json.dumps({"ok": True, "value": result}), flush=True)
        except Exception as error:
            print(json.dumps({"ok": False, "error": str(error)}), flush=True)
finally:
    errors = cleanup()
    if not ready:
        errors.append("Hardware PWM initialization failed")
    print(json.dumps({"ok": not errors, "value": 0, "error": "; ".join(errors)}), flush=True)
