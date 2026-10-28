# car_pi

独立 Java 21 小车迁移工程。当前仅 recording 后端；不输出 GPIO/PWM，不支持图像采集和识别。
构建需要 JDK 21 或更新版本，使用 `--release 21` 生成 Java 21 字节码；Pi 仅需 Java 21 JRE。

```sh
./gradlew test installDist distTar
cd build/install/car-pi
bin/car-pi doctor --output results/doctor.json
bin/car-pi verify --suite protocol --output results/protocol.json
bin/car-pi serve --config config/recording.properties
```

`serve` 默认 TCP 8010，单控制客户端；SIGTERM 释放监听、连接、调度器和 recording 文件。
配置内相对路径相对于启动目录。动作日志为 JSONL，`command` 为本次运行递增序号，延迟动作保留来源序号；0 为关闭动作。
`verify protocol` 在本机临时端口启动真实服务，检查完整协议和动作记录；它不能替代旧手机操作。
未知验收套件返回 BLOCKED，非零退出；不能据此宣称硬件通过。

部署：`scripts/deploy.sh pi@<IP>`，使用 SSH 密钥/agent 或交互输入，不保存密码。只上传版本目录，不安装或切换服务。
Pi 安装运行时：`sudo apt-get update && sudo apt-get install openjdk-21-jre-headless`。
各阶段状态和人工验收要求见 `doc/migration/pi3-java-plan.md`。
