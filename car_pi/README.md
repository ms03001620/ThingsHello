# car_pi

独立 Java 21 小车迁移工程。当前仅 recording 后端；不输出 GPIO/PWM，支持固定 JPEG 预览，不支持真实摄像头采集和识别。
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
