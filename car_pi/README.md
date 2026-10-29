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
