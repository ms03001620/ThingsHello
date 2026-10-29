# car_pi

独立 Java 21 小车迁移工程。默认 recording 后端；支持固定 JPEG 预览，已新增 rpicam 采集和实验性 pigpio 外设适配器。真实外设与实时图像尚未验收；识别暂不支持。
构建需要 JDK 21 或更新版本，使用 `--release 21` 生成 Java 21 字节码；Pi 仅需 Java 21 JRE。

```sh
./gradlew test installDist distTar
cd build/install/car-pi
bin/car-pi doctor --output results/doctor.json
bin/car-pi verify --suite protocol --output results/protocol.json
bin/car-pi verify --suite fixture-video --output results/fixture-video.json
bin/car-pi serve --config config/fixture.properties
```

`serve` 默认 TCP 8010，单控制客户端；SIGTERM 释放监听、连接、调度器和 recording 文件。
配置内相对路径相对于启动目录。动作日志为 JSONL，`command` 为本次运行递增序号，延迟动作保留来源序号；0 为关闭动作。
`verify protocol` 在本机临时端口启动真实服务，检查完整协议和动作记录；它不能替代旧手机操作。
未知验收套件返回 BLOCKED，非零退出；不能据此宣称硬件通过。

部署：`scripts/deploy.sh pi@<IP>`，使用 SSH 密钥/agent 或交互输入，不保存密码。只上传版本目录，不安装或切换服务。
Pi 安装运行时：`sudo apt-get update && sudo apt-get install openjdk-21-jre-headless`。
各阶段状态和人工验收要求见 `doc/migration/pi3-java-plan.md`。

固定图像使用 `config/fixture.properties`：TCP 8010、UDP 8000，320×240 测试色条；相机命令关闭时释放 UDP socket，重复打开重新握手。未接外设时无需任何 GPIO 软件。

`deploy/car-pi.service.in` 为 systemd 模板，安装前替换 `@USER@` 和 `@ROOT@` 为实际运行用户与绝对目录，外置配置保存在 `<root>/config/service.properties`。将 `actions.path` 改为 `<root>/results/actions.jsonl`。默认不自动安装服务。

Pi 上切换：`scripts/activate.sh /home/pi/thingshello car-pi-<version>`；成功后 `previous` 保留上一个版本。回滚时将 previous 指向的版本名传给同一脚本。配置独立保存，修改前需备份；不要使用不兼容的配置回滚旧版本。脚本先自动验收，再切换；启动失败恢复原版本。只适用于已安装的 `car-pi.service`。

## 新增适配器（2026-10-29）

- `config/rpicam.properties`：GPIO 仍为 recording，收到打开摄像头命令后启动 `rpicam-vid`，320×240、5fps、MJPEG。完整 JPEG 解析包含段长度、转义字节与多帧处理。stdout/stderr 分开消费，最多保留一帧与 8 KiB 诊断，单帧采集缓存上限 2 MiB。JPEG 超过 65506 bytes 时记录丢帧，不发送非法 UDP 包。
- 缺少摄像头不会妨碍 TCP 服务启动。采集异常会清理 UDP/子进程，类型 5 信息包含 `error`、`sentFrames`、`droppedFrames`；下一次打开可重试。首次取得真实帧前，不报告已支持的摄像头尺寸。
- `bin/car-pi verify --suite camera --output results/camera.json` 只验证单帧，不代表 P6 全阶段通过；无摄像头返回 BLOCKED、退出 2。`python3 scripts/missing-camera-smoke.py` 仅用于无摄像头 Pi，验证重复失败后控制仍可用。
- `config/pi3-pigpio.properties.example`：实验性真实 IO 配置。复制后明确确认接线、启用需要的设备并填写有效电平。舵机必须填写实际 BCM 引脚；不能把旧 Android PWM0 当成编号。重叠引脚、缺失极性及未确认配置在连接 daemon 前拒绝。
- pigpio 实现通过本机 daemon 的 TCP 接口发送数字输出、PWM 与舵机命令，不需要 Java JNI 或 Java 定时模拟 PWM；当前 Pi 上尚未安装或验证 daemon。连接失败或 native 错误明确报告，不静默退回录制模式。
- 双轮目标仍为 300Hz；标准 pigpio PWM 会取邻近频率（默认采样率通常为 320Hz）。默认 `pwm.maxErrorHz=0` 会拒绝不匹配，不会宣称精确支持 300Hz。若器件允许偏差，需依据规格显式填写容差；否则需另选 native 波形方案。该问题仍阻塞 P3 的最终通过。
- 外设异常会尝试停用所有已启用设备并锁定后端，重启才可重新使用。daemon 连接已丢失时无法保证远端物理输出已停止；日志只证明调用意图与返回值，不能替代波形或物理观察。

实现参考：[Raspberry Pi rpicam 官方说明](https://www.raspberrypi.com/documentation/accessories/camera-software.html)、[pigpio 官方客户端协议与频率表](https://github.com/joan2937/pigpio/blob/master/pigpio.py)。未将这些实现作为库复制或打包。

## USB 摄像头实测入口

当前 USB UVC 设备应使用 `config/usb.properties`（V4L2 原生 MJPG）；部署时将 `camera.device` 设置为 `/dev/v4l/by-id/usb-lihappe8_Corp._USB_2.0_Camera-video-index0`。源端采集 30fps，Java 取最新帧以约 5fps 发送。

- 单帧：`bin/car-pi verify --suite camera --config config/usb.properties --output results/usb-camera.json`。
- 10 次启停与 10 分钟运行：在 Pi 分发目录执行 `python3 scripts/verify-usb.py --duration 600 --cycles 10`，使用独立的 18010/18000 端口和 recording 外设，不操作 GPIO。
- 局域网实图：`python3 scripts/network-smoke.py <PI_IP> --expect live --output results/live.json`，结果旁保存收到的 JPEG。
- `droppedFrames` 当前统计超过 UDP 大小上限的帧；源端 30→5fps 主动取样不在该计数内。资源采样中的 RSS/CPU 为 Java 进程，不包含采集子进程。

真实硬件测试表见 `doc/migration/hardware-test-table.md`。

## 手机车轮控制（2026-10-30）

如果摄像头可见但车轮不动，先检查服务实际使用的配置：`backend=recording` 只写动作日志，不驱动 GPIO。成功收到 `wheel.speed` 并不等于硬件已接入。

当前真实车轮配置为 `config/pi3-lgpio.properties`。它使用系统 `python3-lgpio` 的原生软件定时 PWM，Java 通过专用子进程调用，不运行 pigpiod、不采用 Java/Python sleep 生成 PWM。默认配置频率 300Hz；`pwm.configuredHz` 记录配置值，不能当作仪器测得的波形频率。

引脚维持旧 app：左轮 21/20/16，右轮 26/19/13；正向分别为 BCM20/19 高电平。灯和蜂鸣器一并接入真实输出。舵机仍需独立的硬件 PWM 路径，lgpio 配置暂不启用舵机。

控制连接断开时释放轮子；服务停止时先清理 GPIO 子进程。systemd 应使用模板中的 `KillMode=mixed`，让 Java 先做有序清理。切换到实机配置前应确认接线、车轮架空，并备份旧配置。

在 Pi 分发目录执行 `python3 scripts/verify-control.py --allow-motion` 可做有限时长的物理轮动测试，验证归零、断连、SIGTERM 后使能低电平；它会真实驱动车轮，仅限架空时使用。


### BCM18 硬件 PWM 舵机（0.5.2-servo）

`lgpio` 后端现在允许 `servo.enabled=true`、`servo.pin=18`；worker 对舵机访问 `/sys/class/pwm/pwmchip0/pwm0`，不通过 lgpio 申请 BCM18。周期 20 ms、脉宽 800..1500 us，协议类型 4 及 500 ms 释放不变。之前版本的“舵机未启用”说明仅指历史部署。

部署前，在 `/boot/firmware/config.txt` 的 `[all]` 节配置 `dtoverlay=pwm,pin=18,func=2`；首次启用可运行 `sudo dtoverlay pwm pin=18 func=2`（已有 overlay 时不要重复加载）。此硬件 PWM 与板载模拟音频冲突。

从发布目录安装准备服务：

```sh
sudo install -m 755 deploy/prepare-servo-pwm.sh /usr/local/sbin/car-pi-prepare-servo
sudo install -m 644 deploy/car-pi-servo-pwm.service /etc/systemd/system/
sudo mkdir -p /etc/systemd/system/car-pi.service.d
sudo install -m 644 deploy/servo-pwm.conf /etc/systemd/system/car-pi.service.d/
sudo systemctl daemon-reload
sudo systemctl start car-pi-servo-pwm.service
```

准备服务以 root 导出 PWM0、关闭输出并赋予 gpio 组写权限；Java 与 worker 仍以 pi 运行。服务依赖保证重启后先准备 PWM。禁用舵机并回退时恢复旧配置、移除 `servo-pwm.conf` 后 daemon-reload；只恢复旧程序不足以回退配置。
