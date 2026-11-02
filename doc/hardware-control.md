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

如需开机常驻，在 `/boot/firmware/config.txt` 加 `dtoverlay=pwm,pin=18,func=2`，并配合 `car_pi/deploy/` 下的 `car-pi-servo-pwm.service` 在启动前自动导出和授权 `pwm0`。
注意：PWM0 与板载模拟音频共用，启用后 3.5mm 音频不可用。

---

## 摄像头

小车只使用 CSI 排线摄像头（OV5647），视频由独立的 `car-pi-rtsp` 服务（MediaMTX，H.264 RTSP）提供，不经过 `car_pi`。配置见 `car_pi/deploy/csi/`，延迟优化见 `doc/csi-h264-latency-optimization.md`。

## Java 运行集成说明

- 生产配置 `car_pi/config/pi3-lgpio.properties` 使用 `lgpio` 后端驱动左右轮（BCM 21/20/16、26/19/13）、RGB 灯（BCM 22/27/24）和蜂鸣器（BCM 8）。
- 舵机由 worker 单独操作内核 sysfs 硬件 PWM0（`servo.enabled=true`、`servo.pin=18`），不使用软件 PWM 驱动舵机。
- 若启动时 sysfs `pwm0` 节点未就绪或无写权限，worker 会打印告警并自动降级禁用舵机，不影响车轮、灯光和蜂鸣器正常工作。
