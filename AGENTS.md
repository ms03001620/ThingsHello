# Agent 项目导航

本文件是仓库级导航。先按任务定位模块，再阅读对应入口和实现；新增模块或移动入口时同步更新本文件。下文路径均相对仓库根目录。

## Pi 3 Java 小车服务（`car_pi`）

`app` 小车系统已完成向 Raspberry Pi OS Lite + Java 21 的迁移，保留 `app/` 原源码不动，独立工程位于 `car_pi/`，各外设与视频链路均已实测通过。通信协议见 [小车通信协议说明](doc/protocol.md)，树莓派引脚与硬件驱动方式见 [小车硬件控制方法](doc/hardware-control.md)。

## 项目概况与阅读顺序

ThingsHello 是以 Java 为主的 Android 多模块项目，核心用途是通过 Android 手机控制 Android Things / Raspberry Pi 小车，并接收摄像头画面；同时包含图像识别、FFmpeg 推流和外设实验应用。

1. 了解使用流程：[app_mobile/使用说明.md](app_mobile/使用说明.md)。恢复旧设备连接看 [app_mobile/恢复连接.md](app_mobile/恢复连接.md)。
2. 确认模块范围：[settings.gradle](settings.gradle)，依赖关系看各模块 `build.gradle`。
3. 主业务从小车端 [MainActivity](app/src/main/java/org/mark/thingshello/MainActivity.java) 和手机端 [MainActivity](app_mobile/src/main/kotlin/org/mark/mobile/MainActivity.kt) 开始。
4. 页面布局、字符串和权限分别查目标模块的 `src/main/res/`、`src/main/AndroidManifest.xml`。

## 模块地图

表中的源码根目录用于定位后文的包和类名。

| 模块 | 职责 | 源码根目录 / 首读入口 |
| --- | --- | --- |
| `car_pi` | 独立 Java 21 小车服务，不加入 Android 构建 | `car_pi/README.md`；`car_pi/src/main/kotlin/org/mark/carpi/CarMain.kt`；硬件实现在 `car_pi/src/main/kotlin/org/mark/carpi/repository/linux/`（分层：model/ usecase/ repository/ session/ protocol/ runtime/）；在 `car_pi` 内运行 `./gradlew test installDist` |
| `app` | 小车主应用：外设控制、接收命令、摄像头服务 | `app/src/main/java/org/mark/thingshello/`；`MainActivity.java` |
| `app_mobile` | 手机主应用：连接、摇杆遥控、RTSP 视频预览（纯 Kotlin · MVVM + UseCase） | 先读 [`app_mobile/AGENTS.md`](app_mobile/AGENTS.md)；`app_mobile/src/main/kotlin/org/mark/mobile/`；`MainActivity.kt`、`CtrlActivity.kt` |
| `lib_unit_socket` | 两端共用的 TCP / UDP 通信、命令类型及数据对象 | `lib_unit_socket/src/main/java/org/mark/lib_unit_socket/`；`SocketManager.java`、`ConnectedThread.java`、`bean/` |
| `lib_camera` | Camera2 摄像头与采集配置封装 | `lib_camera/src/main/java/org/mark/camera/`；`DoorbellCamera.java`、`ConfigFactory.java`、`IConfig.java` |
| `lib_base` | 通用线程、图像处理、文件及偏好设置工具 | `lib_base/src/main/java/org/mark/base/`；`thread/`、`executor/` |
| `lib_tensorflow` | TensorFlow Lite 分类器封装 | `lib_tensorflow/src/main/java/org/mark/lib_tensorflow/`；`ImageClassifier.java`、`Classifier.java` |
| `app_prework` | 模型配置、分类预览、图片检查与 FFmpeg 推流实验 | `app_prework/src/main/java/org/mark/prework/`；启动页 `cam/ffmpegpush/FFmpengPushActivity.java` |
| `lib_ffmpeg` | FFmpeg JNI 库 | `lib_ffmpeg/src/main/java/org/mark/ffmpeg/FFUtils.java`、`src/main/cpp/native-lib.cpp`、`CMakeLists.txt` |
| `app_rtmp` | 独立摄像头推流实验，包含自己的 FFmpeg JNI 实现 | `app_rtmp/src/main/java/org/mark/prework/`；`MainActivity.java`、`MainPresenter.java`；`src/main/jni/ffmpeg-handler.c` |
| `app_test` | Android Things 外设测试应用，目前入口测试蜂鸣器 | `app_test/src/main/java/org/mark/things/test/`；`MainActivity.java`、`BuzzerAction.java` |

## 按工作内容定位

| 要处理的工作 | 优先查看 |
| --- | --- |
| 手机连接、连接状态、IP 保存、MVVM 与本地编码约定 | 先读 [`app_mobile/AGENTS.md`](app_mobile/AGENTS.md)；`app_mobile`：`MainActivity.kt`、`viewmodel/MainViewModel.kt`、`usecase/ConnectCarUseCase.kt`、`repository/CarControlRepository.kt`、`repository/CarPrefsRepository.kt`、`connect/ConnectedManager.kt`、`utils/PreferUtils.kt` |
| 遥控页面、摇杆、速度、转向、灯光按钮 | 先读 [`app_mobile/AGENTS.md`](app_mobile/AGENTS.md)；`app_mobile`：`CtrlActivity.kt`、`viewmodel/CtrlViewModel.kt`、`usecase/DriveJoystickUseCase.kt`、`repository/CarControlRepository.kt`、`ctrl/`、`ui/cyber/`、`src/main/res/layout/` |
| 命令格式、类型、新增控制指令 | `lib_unit_socket`：`bean/CmdConstant.java`、`bean/*Cmd.java`、`bean/JsonReceiver.java`；同时检查手机发送处和小车分发处 |
| TCP 连接、读写、服务端接入 | `lib_unit_socket`：`SocketManager.java`、`SocketService.java`、`ConnectedThread.java`；手机连接封装在 `app_mobile/connect/` |
| 小车命令分发、硬件初始化、引脚 | `app`：`MainActivity.java`、`ctrl/DeviceManager.java`、`ctrl/DeviceHelper.java`、`ctrl/BoardDefaults.java` |
| 车轮、摄像头舵机、灯、蜂鸣器 | `app`：`ctrl/wheel/`、`ctrl/servo/`、`ctrl/light/`、`ctrl/voice/`；车轮与舵机互斥见 `ctrl/comp/bind/ExclusiveBind.java` |
| 小车摄像头开关、采集与图像发送 | `app`：`video/CameraServiceConnection.java`、`video/CameraService.java`、`video/CameraPresent.java`、`video/sender/`；底层见 `lib_camera` |
| 手机预览、图像接收、画面渲染 | 先读 [`app_mobile/AGENTS.md`](app_mobile/AGENTS.md)；`app_mobile`：`CtrlActivity.kt`（RTSP 小窗）、`H264Activity.kt`（全屏）、`viewmodel/H264ViewModel.kt`、`usecase/CalculateCatchUpSpeedUseCase.kt`、`preview/LowLatencyRenderersFactory.kt` |
| 模型加载、分类器与标签 | `lib_tensorflow`；`app/tensorflow/ImageClassifierFactory.java`；模型与标签在 `app/src/main/assets/` |
| 分类预览、结果排序和平滑 | `app_prework`：`cam/TfliteConfigActivity.java`、`cam/preview/`、`cam/preview/category/`；绑定模型在 `src/main/ml/` |
| 图片检查与网格浏览 | `app_prework`：`CheckActivity.java`、`CheckPresent.java`、`grid/`、`db/DbMock.java` |
| FFmpeg / RTMP 推流 | 先确认是 `app_prework/cam/ffmpegpush/` → `lib_ffmpeg`，还是 `app_rtmp/MainPresenter.java` → `com/david/camerapush/ffmpeg/FFmpegHandler.java` → 自有 JNI |
| 历史控制脚本、示意资料 | `doc/TCP_Control.py`、`doc/TCP_control.c`、`doc/da.py`、`README.md` |

上表中的 `app/tensorflow/`、`app_mobile/connect/` 等缩写均指模块地图中源码根目录下的子目录；`src/main/...` 指对应模块下的路径。

## 核心调用链

**控制命令（TCP 8010）**

`app_mobile/CtrlActivity` / `ctrl/` → `ConnectedManager` → `ConnectedWifi` → 共用通信层 → 小车 `SocketManager` → `app/MainActivity` → `DeviceManager`（外设）或 `CameraServiceConnection`（摄像头）。

**图像预览（RTSP 8554）**

手机 `CtrlActivity` 小窗 / `H264Activity` 全屏通过 ExoPlayer 拉取 `rtsp://小车IP:8554/csi`（RTP over TCP），由 `car_pi/deploy/csi/` 的 CSI 摄像头 RTSP 服务提供。控制连接正常但无画面时，沿这条独立链路排查。旧的 UDP 8000 JPEG 预览已从 `app_mobile` 和 `car_pi` 移除，仅 `app`（Android Things 小车端）仍保留发送端代码。

## 构建与验证入口

- 根配置：`build.gradle`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`；当前声明 AGP `7.1.3`、Gradle `7.6.6`，各模块 `compileSdkVersion` 为 `28`。具体配置以文件为准。
- SDK 本机路径见 `local.properties`；不要将本机路径复制到共享配置。
- 单元测试位于各模块 `src/test/java/`。
- 有针对性的测试包括：`app` 的 `CameraServoTest`、`ExclusiveBindTest`，`lib_unit_socket` 的 `ConnectedThreadTest`，`lib_base` 的 `CameraUtilsTest`，`app_prework` 的 `cam/preview/category/*Test`。

在仓库根目录按改动范围选择命令：

```bash
# 主应用构建
./gradlew :app:assembleDebug :app_mobile:assembleDebug

# 控制逻辑和通信层单元测试
./gradlew :app:testDebugUnitTest :lib_unit_socket:testDebugUnitTest

# 分类结果算法单元测试
./gradlew :app_prework:testDebugUnitTest

# 其他模块：将 MODULE 替换为实际模块名
./gradlew :MODULE:assembleDebug
```

这些是验证入口，不代表当前环境已验证通过。JNI 模块还需要对应 NDK / CMake 和本地 `.so` 依赖；构建产物在各模块 `build/outputs/`。

## 定位时容易混淆的地方

- `app_test` 是可安装的外设测试应用；自动化单元测试要找 `src/test/`。
- `app_prework` 与 `app_rtmp` 都使用 `org.mark.prework` 包名，且当前 `applicationId` 相同。搜索或修改 `MainActivity`、`App` 等同名类时务必限定模块；两者安装到同一设备会共用应用标识。
- `app_rtmp` 自带 FFmpeg JNI；其 `lib_ffmpeg` 依赖目前被注释。修改推流前先确认实际应用。
- `CameraUtils` 在 `lib_base` 和 `lib_camera` 中各有一份；`PreferUtils` 在 `lib_base`（通用底层）和 `app_mobile`（手机专属单例，统一管理配置）中各有一份，`app_mobile` 内部已统一使用自身模块的 `PreferUtils`。
- `app` 与 `app_test` 各有 `com/leinardi/android/things/pio/SoftPwm.java`，修改 PWM 时核对两处用途。
- 小车硬件初始化受 `DeviceManager` 中 `iot_rpi3` 型号判断限制；普通手机或模拟器上的构建/启动结果不能替代外设验证。设备使用细节见前述使用说明。
- 搜索优先限定目标模块的 `src/main/java/` 和 `src/main/res/`，默认排除 `build/`、`.gradle/`、`.idea/`；模型、FFmpeg 头文件和二进制库仅在相关任务中展开。

## 排线 H.264 / RTSP 新预览

720p 低延迟根因与优化记录：[doc/csi-h264-latency-optimization.md](doc/csi-h264-latency-optimization.md)。Pi 服务配置与部署：`car_pi/deploy/csi/`；实机验证脚本：`car_pi/hardware-tests/`；手机入口 `CtrlActivity` 的 `btn_fullscreen` / 小窗点击，独立页面 `app_mobile/.../H264Activity.kt`。car_pi 已移除旧的 USB JPEG/UDP 视频通路。
