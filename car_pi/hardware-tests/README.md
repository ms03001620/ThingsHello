# 实机验证脚本（hardware-tests）

这里的脚本只能在小车的 Pi 上手动运行。它们会启动真实的 `car-pi` 进程，部分脚本还会驱动车轮或打开摄像头。日常的代码逻辑测试在 `src/test/java`，用 `gradle test` 运行，不会碰硬件。两者不要混用。

## 运行前准备

1. 部署后，脚本会跟随发布包放在 `<发布目录>/hardware-tests/`。**所有脚本都要在发布目录的根目录下运行**，因为它们依赖相对路径 `bin/car-pi` 和 `config/`：
   ```bash
   cd ~/car-pi/current        # 按实际部署根目录调整
   python3 hardware-tests/<脚本>.py
   ```
2. 脚本使用的端口和线上服务错开（除 `lifecycle.py` 外）。不过为了避免两个进程抢同一个 GPIO 或摄像头，**建议先停掉线上服务**：
   ```bash
   sudo systemctl stop car-pi car-pi-rtsp
   ```
   测试完成后，再用 `sudo systemctl start car-pi car-pi-rtsp` 恢复。
3. 结果写在 `results/` 下。每个脚本运行结束时，会在终端打印一行 JSON。

## 怎么看结果

- **通过**：终端最后打印 `PASS`，或者打印的 JSON 里有 `"status": "PASS"`，进程退出码为 0。
- **失败**：会抛出异常，打印 Traceback，退出码不为 0。部分脚本会在结果 JSON 里写 `"status": "FAIL"` 和 `reason`。排查时先看对应的 `*.stdout` 或 `*.log` 里的服务日志。

## 脚本一览

| 脚本 | 验证内容 | 前提 / 风险 | 运行方式 | 结果文件 |
|---|---|---|---|---|
| `verify-control.py` | 左右车轮分别转动后，发送 0 指令能停下；TCP 断开后自动停车；SIGTERM 后电机断电（读取 BCM16、BCM13 引脚电平，应为低电平） | **车轮必须架空**，会真的转动。使用 `config/pi3-lgpio.properties`，端口 18012，摄像头关闭 | `python3 hardware-tests/verify-control.py --allow-motion` | `results/control-test.json` |
| `lifecycle.py` | 服务连续 3 次“启动、SIGTERM 退出、端口可以重新绑定” | 使用模拟硬件（`hardware.mode=simulated`），不输出 GPIO。**会占用 8010 端口**，所以必须先停掉 `car-pi` | `python3 hardware-tests/lifecycle.py` | `results/lifecycle.json` |
| `verify-usb.py` | USB 摄像头反复开关 10 次不残留子进程；长时间连续出图，同时记录内存和 CPU | 需要接 USB 摄像头（设备路径写在脚本里，换摄像头要改）。默认运行 600 秒 | `python3 hardware-tests/verify-usb.py --duration 600 --cycles 10` | `results/usb-hardware/result.json`、`first.jpg`、`last.jpg` |
| `missing-camera-smoke.py` | 没有摄像头时，开视频会报错，但 TCP 控制仍然正常 | **只能在没接摄像头时运行**；接着摄像头时会失败，这是预期结果 | `python3 hardware-tests/missing-camera-smoke.py` | `results/missing-camera.json` |
| `network-smoke.py` | 从局域网另一台机器连接**正在运行的** `car-pi`，验证 TCP 指令通、UDP 能收到 JPEG 画面 | 在 Mac 上运行，不需要停服务。需要 `car-pi` 正在使用 USB/JPEG 视频；排线摄像头走的是 RTSP，不适用 | `python3 car_pi/hardware-tests/network-smoke.py 192.168.8.190 --expect live` | `results/network.json`，加 `--expect live` 时还会保存一张 `.jpg` |

## 建议什么时候跑

- 改了车轮、PWM、`lgpio` 或停车逻辑：跑 `verify-control.py`。
- 改了服务启动、退出、systemd 配置：跑 `lifecycle.py`。
- 改了 USB 摄像头或 JPEG/UDP 链路：跑 `verify-usb.py` 和 `network-smoke.py`。
- 排线摄像头（MediaMTX/RTSP）目前没有自动化脚本，用手机 App 的 H.264 页面目测，参考 `doc/csi-h264-latency-optimization.md`。
