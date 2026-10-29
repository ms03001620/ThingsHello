# 小车硬件控制方法

## 摄像头舵机（Pi 3 实测，2026-10-30）

### 结论

- 舵机信号线接 **BCM18**（物理引脚 12），即 Pi 的硬件 **PWM0**（ALT5 功能）。
- **必须使用硬件 PWM**。用 lgpio 软件 PWM（`tx_servo`）在 BCM18/23/9/11 上实测舵机均不动。
- 舵机由底板电池供电；开机时舵机会轻微抖动一下，属正常现象。
- `car_pi` 配置中填写 `servo.pin=18`，不要写旧 Android 的名字 `PWM0`。

### 参数（来自旧版 app `CameraServo.java`）

| 项目 | 值 |
| --- | --- |
| 频率 | 50 Hz（周期 20 ms） |
| 脉宽范围 | 0.8 ms 到 1.5 ms（旧代码中的仰角限制） |
| 协议 | TCP 指令类型 4（`CAMERA_SERVO`），JSON `{"progress": 0..100}` |
| 换算 | 脉宽(ms) = 0.8 + 0.007 × progress；占空比 = 脉宽 / 20 × 100% |
| 自动释放 | 每次指令后 500 ms 关闭 PWM，避免舵机持续抖动/发热 |

旧版中舵机与轮子通过 `ExclusiveBind` 互斥：控制舵机时会先释放轮子，反之亦然。

### 在 Pi 上手动测试（内核 sysfs 硬件 PWM）

```sh
sudo dtoverlay pwm pin=18 func=2          # 临时启用 PWM0 到 BCM18，重启后失效
echo 0 | sudo tee /sys/class/pwm/pwmchip0/export
P=/sys/class/pwm/pwmchip0/pwm0
echo 20000000 | sudo tee $P/period         # 20 ms，单位 ns
echo 1150000  | sudo tee $P/duty_cycle     # 1.15 ms，中间位置
echo 1        | sudo tee $P/enable
echo 800000   | sudo tee $P/duty_cycle     # 一端
echo 1500000  | sudo tee $P/duty_cycle     # 另一端
echo 0        | sudo tee $P/enable         # 停止输出
```

确认引脚功能：`pinctrl get 18` 应显示 `a5 ... GPIO18 = PWM0`。

如需开机常驻，在 `/boot/firmware/config.txt` 加 `dtoverlay=pwm,pin=18,func=2`。
注意：PWM0 与板载模拟音频共用，启用后 3.5mm 音频不可用。

### 用 pigpio 控制（car_pi 的 pigpio 后端）

pigpiod 的 `set_servo_pulsewidth(18, 800..1500)` 走 DMA 定时；本轮没有验证该路径的舵机动作及波形精度，已通过的是上面的 sysfs 硬件 PWM。
2026-10-30 已在 Pi 本地源码编译并启动 pigpio V79，但正常退出测试未通过，尚未安装常驻服务。具体记录见 [硬件测试表](hardware-test-table.md)。

---

## 摄像头（Pi 3 实测，2026-10-30）

### 结论

- 摄像头是 **USB UVC 免驱摄像头**（USB ID `038f:6001`），设备节点 `/dev/video0`，不是 CSI 排线摄像头。
- `rpicam-still` / `rpicam-vid` 报 `no cameras available`，**不能用**；`config/rpicam.properties` 不适用于这台车。
- 应使用 V4L2：`car_pi` 配置用 `config/usb.properties`（`camera=v4l2`），部署时把 `camera.device` 改成 `/dev/v4l/by-id/usb-lihappe8_Corp._USB_2.0_Camera-video-index0`。
- Pi 上已有 `v4l2-ctl`，没有 `ffmpeg`。

### 支持的格式

| 格式 | 分辨率 | 帧率 |
| --- | --- | --- |
| MJPG | 640×480、352×288、320×240 等 | 30 fps |
| YUYV | 640×480、352×288、320×240、176×144、160×120 | 30 fps |

`V4l2FrameSource` 要求原生 MJPG 320×240，本摄像头满足，无需 YUYV 转 JPEG。

### 在 Pi 上手动拍照

```sh
v4l2-ctl -d /dev/video0 --set-fmt-video=width=640,height=480,pixelformat=MJPG \
  --stream-mmap --stream-count=15 --stream-to=/tmp/f.mjpg
# 取最后一帧（前几帧可能曝光未稳定）
python3 -c "d=open('/tmp/f.mjpg','rb').read().split(b'\xff\xd8'); open('/tmp/shot.jpg','wb').write(b'\xff\xd8'+d[-1])"
```

查看格式：`v4l2-ctl -d /dev/video0 --list-formats-ext`。

### 视频传输协议

- TCP 指令类型 3（`CAMERA`）开/关摄像头，类型 5 返回摄像头信息（含 `error`、`sentFrames`、`droppedFrames`）。
- 图像以单帧 JPEG 通过 UDP 8000 发送，单包上限 65506 字节，超出的帧丢弃。320×240 MJPG 一般远小于此上限。
