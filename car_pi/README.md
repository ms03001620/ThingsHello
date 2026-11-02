# car_pi

树莓派小车控制与摄像头服务（Java / Kotlin，JVM 21），由原 Android Things `app` 模块迁移而来，运行于 Raspberry Pi OS Lite。
服务默认通过 TCP `8010` 端口接收手机端 `app_mobile` 的控制指令（车轮驱动、RGB 灯光、蜂鸣器、云台舵机），
并通过 UDP `8000` 端口回传摄像头画面；同时支持独立部署排线摄像头 H.264 RTSP 视频流服务。

构建需 JDK 21 及以上版本，树莓派运行需安装 Java 21 JRE。

## 项目类型与推荐 IDE

- **项目类型**：独立的 **Java / Kotlin（JVM 21）应用程序工程**，使用自带的 Gradle `9.5.0` Wrapper、
  `application` 与 Kotlin JVM `2.4.21` 插件构建，不依赖 Android SDK，也未纳入外层 Android 总项目的 `settings.gradle`
  （避免与旧版 AGP/Gradle 版本冲突）。
- **推荐开发 IDE**：
  1. **IntelliJ IDEA**（首选，Community 社区版即可）：直接以 `car_pi` 目录作为项目根目录打开
     （`File -> Open -> 选择 car_pi`），将 Gradle JVM 设为 **JDK 21+**。
  2. **Android Studio**（可直接复用现有环境）：通过 `File -> Open`
     **单独选择 `car_pi` 目录并在新窗口（New Window）打开**，不要混用在外层 `ThingsHello` 窗口中，
     并在项目 Gradle 设置里将 Gradle JVM 设为 **JDK 21+**。
  3. **VS Code / Antigravity**：安装 `Extension Pack for Java` 插件，直接将 `car_pi` 目录作为工作区打开。

## Kotlin 开发

- Kotlin 业务代码放在 `src/main/kotlin/`，Kotlin 测试放在 `src/test/kotlin/`，按包名建立子目录。
- 现有 Java 源码和测试继续放在 `src/main/java/`、`src/test/java/`，可与 Kotlin 相互调用。
- Java 与 Kotlin 均编译为 JVM 21 字节码；Kotlin 标准库由插件自动引入并包含在分发包中。
- 测试沿用 JUnit 5，执行 `./gradlew test installDist` 完成测试和分发包构建。

## 启动与关闭

### 1. 命令行运行

先构建可执行分发包：

```sh
./gradlew installDist
```

**启动服务：**

```sh
# 树莓派真实硬件环境
./build/install/car-pi/bin/car-pi serve --config config/pi3-lgpio.properties

# 本地无硬件模拟环境（不传 --config 默认以 recording 模式运行）
./build/install/car-pi/bin/car-pi serve
```

**关闭服务：**

- 前台运行：按 `Ctrl + C` 退出。
- 后台运行：执行 `kill <PID>` 发送 `SIGTERM` 信号，服务会自动关闭监听端口、停止摄像头采集并释放 GPIO 资源。

### 2. systemd 服务管理（树莓派已安装服务时）

**启动服务：**

```sh
sudo systemctl start car-pi
```

**关闭服务：**

```sh
sudo systemctl stop car-pi
```

## 目录说明

- `config/`：树莓派实车硬件运行配置（`pi3-lgpio.properties`）。
- `deploy/`：系统部署配置，包含 systemd 服务单元模板、硬件初始化脚本、流媒体服务配置及命令手册（[deploy/README.md](deploy/README.md)）。
- `gradle/`：Gradle Wrapper 构建包装器配置。
- `hardware-tests/`：树莓派实机验证脚本（控制、USB 摄像头、网络、生命周期、缺失摄像头冒烟测试），需在发布目录根下运行，运行方式与结果判定见 [hardware-tests/README.md](hardware-tests/README.md)。
- `scripts/`：版本打包上传与树莓派端版本切换脚本。
- `src/`：Java / Kotlin 业务源码、资源文件及单元测试代码。
