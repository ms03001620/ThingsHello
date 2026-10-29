# GPIO worker: PWM timing comes from native liblgpio, not this Python loop.
import json, signal, sys, time
import lgpio

inactive = {int(pin): int(value) for pin, value in json.loads(sys.argv[1]).items()}
handle = lgpio.gpiochip_open(0)
claimed, frequencies = set(), {}

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
print(json.dumps({"ok": True, "value": 0}), flush=True)
try:
    for line in sys.stdin:
        try:
            request = json.loads(line)
            op = request["op"]
            if op == "close":
                break
            pin, value = int(request["pin"]), int(request["value"])
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
    print(json.dumps({"ok": not errors, "value": 0, "error": "; ".join(errors)}), flush=True)
