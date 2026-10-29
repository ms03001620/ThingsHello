# Pi 3 Java 小车系统迁移计划

状态：实施中；2026-10-28 已创建 `car_pi/`，安装 Pi Java 21 并部署 recording 分发包。P1 已完成、已验证；P2/P5 自动验收及 P7 recording 服务启停、回滚、整机重启恢复已完成、已验证。部分真实硬件已于 2026-10-29 / 30 测试通过，旧手机联调仍未验证。详见 [硬件测试表](hardware-test-table.md)。

执行状态见下表，验收摘要见 [执行结果](results/2026-10-28.md)。早期仅有 Pi，随后用户接入 USB 摄像头及 GPIO；最新实测状态以下表和硬件测试表为准，不以启动无异常代替硬件验证。2026-10-29 新增实验性适配代码，见下方补充表。

| 项目 | 完成情况 | 验证结果 |
| --- | --- | --- |
| P0 环境基线 | 已完成 | 已验证 |
| P1 Java 骨架、Pi Java 21、退出释放 | 已完成 | 已验证 |
| P2 协议、录制动作自动验收 | 已完成 | 已验证 |
| P2 旧手机控件操作（阶段剩余验收） | 未完成 | 未验证 |
| P3 数字 GPIO / pigpio 构建与启动 | 已完成 | 已验证 |
| P3 精确 300Hz / 波形精度 / native 正常退出 | 未完成 | 未验证 |
| P4 灯、蜂鸣器、基本轮动、sysfs 舵机动作 | 已完成 | 已验证 |
| P4 Java 全外设调速 / 舵机 / 互斥集成 | 未完成 | 未验证 |
| P5 固定图像自动验收（10 次启停） | 已完成 | 已验证 |
| P5 旧手机显示（阶段剩余验收） | 未完成 | 未验证 |
| P6 USB 实拍 / Java UDP / 10 次启停 / 10 分钟运行 | 已完成 | 已验证 |
| P6 原手机实时显示 | 未完成 | 未验证 |
| P7 recording 服务启停、外置配置、回滚 | 已完成 | 已验证 |
| P7 整机重启恢复 | 已完成 | 已验证 |
| P7 30 分钟稳定性、旧手机完整联调（阶段剩余验收） | 未完成 | 未验证 |
| P8 可选识别 | 首轮排除 | 未验证 |

## 2026-10-29 代码补充（当时状态，最新实测见硬件测试表）

已部署 `0.3.2-adapters`，运行配置仍为 recording + fixture。新增代码与硬件验收分开记录，详细证据见 [2026-10-29 结果](results/2026-10-29.md)。

| 补充项 | 代码完成情况 | 软件验证 | 真实硬件验证 |
| --- | --- | --- | --- |
| RpicamFrameSource、完整 MJPEG 解析、进程/线程清理 | 已完成 | 已验证 | 未验证 |
| 缺少摄像头时继续控制、重复打开重试 | 已完成 | 已验证（Pi） | 未验证（实拍） |
| pigpio socket 适配、数字输出/车轮 PWM/舵机控制 | 已完成（实验性） | 已验证（模拟 daemon/IO） | 未验证 |
| 引脚冲突、极性、启用开关、PWM 返回频率检查 | 已完成 | 已验证 | 未验证 |
| native daemon 在本机内核运行、精确 300Hz 方案 | 未完成 | 未验证 | 未验证 |

P3/P4/P6 整阶段仍未完成。标准 pigpio PWM 的离散频率不能直接满足精确 300Hz；默认容差 0 时拒绝不匹配，未静默改用 320Hz。真实输出保持关闭，未修改接线。P8 推理仍不在首轮范围。

## 1. 目标与边界

- 保留 `app/` 原源码不动；旧 Android 库和 `app_mobile/` 同样暂不修改。
- 新建独立 `car_pi/` Java 工程，迁移小车功能，最终在 Raspberry Pi OS Lite 下由 systemd 运行。
- 首轮保持手机协议、控制指令及可观察行为；重点是替换平台依赖和拆分结构，不进行业务改版或旧代码全面修复。
- 每次只迁移一个可在 Pi 上运行的纵向功能，留下结果后再进入下一阶段。编译成功、模拟成功、实机输出成功分别记录。
- `car_pi/` 已建立；可执行入口以其 README 为准，未实现的验收套件返回 BLOCKED。

## 2. 初始实机结果（2026-10-28，保留历史基线）

通过用户提供的 SSH 账号检查，无密码写入仓库。无线地址可用，有线地址本次超时；不将地址写死进程序。

| 项目 | 实际结果 | 对方案的影响 |
| --- | --- | --- |
| 硬件 | Raspberry Pi 3 Model B Rev 1.2 | 以当前 Pi 3 为验收设备 |
| 系统 | Debian 13 trixie，Raspberry Pi reference 2026-10-06，stage2 | 按此系统验证依赖，暂不换系统 |
| 架构 | `aarch64`，`getconf LONG_BIT` = 64 | Java 和 native 库选择 ARM64 |
| Java | `java: command not found` | 第一步需安装运行时 |
| 软件包索引 | OpenJDK 21 headless 有候选版本；OpenJDK 17 无候选版本 | 推荐 Java 21；安装时重新确认实际版本 |
| 摄像头工具 | `/usr/bin/rpicam-hello`、`/usr/bin/rpicam-vid` 存在 | 可走官方摄像头工具适配 |
| 摄像头枚举 | `rpicam-hello --list-cameras` → `No cameras available!` | 摄像头实拍阶段尚不能通过；不等同于已经证明硬件损坏 |
| GPIO | `/dev/gpiochip0/1/2/4`；libgpiod/gpiod 2.2.1 已安装 | 有 GPIO 基础，但芯片与引脚映射仍需确认 |
| PWM | `/sys/class/pwm` 未列出设备；未找到 `pigpiod`；`pigpio` 无候选包 | 必须先做 PWM 技术验证，不能假设安装 Pi4J 即可完成 |
| 资源 | 内存约 905 MiB，根分区空闲约 11 GiB | 优先轻量 Java 进程和外部采集程序 |
| 网络 | 当次 TCP 监听仅见 SSH 22 | 尚无新小车服务监听 8010 |

以上是环境检查结果，不是迁移功能验收结果。未运行电机、蜂鸣器、舵机或 GPIO 输出测试。

## 3. 代码迁移边界

下列 `app` 类路径均相对 `app/src/main/java/org/mark/thingshello/`。

| 现有实现 | 新工程中的处理 |
| --- | --- |
| `MainActivity`、`App` | `CarMain` / `CarRuntime` 显式初始化和关闭资源；去掉 Activity、Context 和型号字符串判断 |
| `ctrl/DeviceManager`、`DeviceHelper`、`OnReceiverCommand` | 迁移为命令分发器与外设控制器，使用配置和构造注入 |
| `ctrl/wheel/WheelAction` | 保留左右轮速度及原地转向语义，调用 `MotorDriver` |
| `ctrl/wheel/Wheel`、`ctrl/light/ForwardLightAction`、`ctrl/voice/BuzzerAction` | 替换 Android Things GPIO / SoftPwm；业务层不接触具体 native API |
| `ctrl/servo/CameraServo`、`ctrl/comp/bind/ExclusiveBind` | 保留位置换算、500ms 自动释放及车轮/舵机互斥；调度换为 Java executor |
| `video/CameraServiceConnection`、`CameraAction`、`CameraService` | 用进程内 `CameraController` 和明确的响应接口替代 Binder/Messenger/Service |
| `video/CameraPresent`、`lib_camera` | 新 `FrameSource` 提供 JPEG；CSI 优先 rpicam 子进程，USB 则先确认 V4L2 格式后选择适配器 |
| `video/sender/UdpSender` | Java DatagramSocket，保持旧握手和 JPEG 数据包格式 |
| `lib_unit_socket` 的 DTO、编解码、socket 逻辑 | 在新工程复制必要部分、保留许可证和来源，移除 Android/AndroidX/org.json 平台依赖；旧库不动 |
| Handler / Looper / WorkThreadHandler / Rx | 单线程命令队列 + ScheduledExecutorService；摄像头读取独立线程，避免阻塞控制 |
| Android Log、SharedPreferences、assets | Java 日志、外部配置、普通资源文件 |
| `tensorflow/Test`、`ImageClassifierFactory` | 首轮延后；当前 CameraPresent 有初始化和分类方法，但采集回调未实际调用分类。保留能力接口并明确报告暂不支持，不能算已迁移 |

不在 Java 工程中引入 android.jar、Android Things stub 或原 Android AAR 来伪装兼容。

## 4. 建议结构与技术选择

```text
car_pi/                         # 独立构建，不加入旧 Android settings.gradle
  settings.gradle
  build.gradle                  # Java 21，application；独立支持 Java 21 的 wrapper
  src/main/java/.../
    runtime/                    # 启动、关闭、配置、能力信息
    protocol/                   # DTO、TCP framing、UDP framing
    control/                    # 命令分发、车轮/舵机互斥、控制器
    hardware/                   # DigitalOutput、PwmOutput、ServoOutput 等接口
    hardware/recording/         # 记录动作；不输出引脚
    hardware/linux/             # 实机适配器
    camera/                     # FixtureFrameSource、RpicamFrameSource
  src/test/                     # 协议样本及必要的行为兼容测试
  config/                       # BCM 引脚、端口、后端、摄像头参数
  scripts/                      # 打包、SSH 部署、探测、验收、结果收集
  deploy/                       # systemd unit 模板
  acceptance/fixtures/          # 固定命令样本与小尺寸 JPEG
doc/migration/                  # 本计划、协议说明、每阶段验收摘要
```

初期一个 Java module、内部按包分层，避免先拆出大量空模块。使用独立构建隔离旧 AGP、Android SDK、NDK 和仓库配置；本机构建分发包，Pi 仅安装 JRE 和所需系统组件。

GPIO 推荐先验证 Java 21 + Pi4J 3.x / pigpio 路径，锁定具体版本后才用于正式适配器。Pi4J 3.x 对应 Java 21，4.x 转向 Java 25，因此不直接依赖“最新版”。当前 Pi 没有 pigpio，先验证可复现的 ARM64 native 构建/安装及现有内核兼容性，不能在计划里写成已支持。

libgpiod 可作为数字输出路径，但它的存在不能证明现有多路 PWM 可实现。若 pigpio 验证失败，明确在以下方案中做决策：兼容当前系统的 native PWM 后端；或外接 PWM 控制器/调整接线。后者属于硬件变化，应单独确定后再实施。不要用 Java sleep 循环替代精确 PWM，也不要为继续业务迁移静默改接线。

摄像头采用 `ProcessBuilder` 管理采集进程并消费 JPEG/MJPEG，读取 stderr、处理退出与关闭。按完整 JPEG 帧解析，不能把一次 stdout read 当成一帧。初始请求使用现有手机的 320×240；帧率先以可测的低值起步，再根据 Pi 实测调整。

## 5. 固定兼容合同

### 通信

- TCP 8010：`uint32 little-endian 正文长度 + 1 byte type + JSON bytes`，不是逐行 JSON、HTTP 或 WebSocket。新端明确使用 UTF-8。
- 类型：0 车轮、1 灯、2 蜂鸣器、3 摄像头、4 舵机、5 摄像头信息、6 原地转向；旧 -1 文本响应在字节层为 0xFF，兼容解析需保留这一语义。
- 字段以 `lib_unit_socket/.../bean/` 为准：`left/right`、`red/green/blue`、`di`、`progress`、`rotate=LEFT|RIGHT` 与 `speed`、`action/width/height/transferVideo/tfCmd`。
- UDP 8000：手机发送 `0x05 + hello`；回复到实际报文来源 IP/端口；每个图像包是 `0x05 + 完整 JPEG`。不能将裸 MJPEG 或 H.264 直接发给旧手机。
- 单个 UDP 包含类型头后必须在 IPv4 UDP 有效载荷上限 65507 bytes 内；超过则调低 JPEG 大小或明确记录丢帧，不新增手机不认识的分片协议。
- 摄像头信息响应需检查 `CameraAction` 与 `lib_camera/CameraUtils.CameraInfo` 的 `sizes/hardwareLevel` 序列化形状，形成样本；新端报告实际支持尺寸，不伪称具备 Android Camera2 硬件等级。

### 外设

| 外设 | 原代码基线 | 实机确认事项 |
| --- | --- | --- |
| 左轮 | BCM21、20 方向；BCM16 PWM，300Hz | 引脚对应物理排针、驱动极性、前后方向 |
| 右轮 | BCM26、19 方向；BCM13 PWM，300Hz | 同上；不能把 BCM19 擅自改作硬件 PWM |
| RGB 灯 | BCM22、27、24；原实现开启时均为 100% | 首轮保持开关语义，不顺带实现新调色行为 |
| 蜂鸣器 | BCM8，500ms 提示 | 有效电平需核对，不能仅机械替换旧 API 常量 |
| 舵机 | Android `PWM0`，50Hz；progress 0..100 → 0.8..1.5ms；500ms 后释放 | `PWM0` 不能直接当成 Linux BCM 编号，必须核对实际线缆和映射 |

保存协议和预期外设动作样本作为迁移依据；旧平台 API 的调用写法不必逐字复制，但物理动作需要实测确认。对既有业务问题单独记录，不扩展为本轮重构任务。

## 6. 逐步实施与 Pi 验收

每阶段独立提交；未通过的阶段不得标记完成。硬件阶段等待接线确认时，可继续协议和固定图像验证。

| 阶段 | 实施内容 | 在 Pi 上执行的验证 | 通过条件与证据 |
| --- | --- | --- | --- |
| P0 环境基线 | 只读探测 OS、架构、Java、设备与端口 | SSH 执行系统命令、摄像头枚举 | 已完成探测；Java 缺失、无摄像头等作为后续前置条件保留 |
| P1 Java 可运行骨架 | 新建 car_pi，安装 Java 21；实现 `doctor`、启动/关闭、配置、recording 后端 | 将同一分发包传到 Pi，执行 `doctor`，前台启动再 SIGTERM；重复 3 次 | Java 主程序实际执行，退出后无残留线程/监听；报告架构、运行时及构建版本；不要求摄像头已连接 |
| P2 手机协议贯通 | DTO、TCP 编解码、分发器、recording 驱动 | Pi 运行服务；验收客户端发送每类命令，含拆包和连续包；旧手机连接 Pi 并操作控件 | 命令字段与动作记录逐项匹配，手机无需改代码；日志可关联命令序号；这只是 Pi 上的模拟外设验收 |
| P3 GPIO/PWM 技术验证 | 接线清单、数字 IO、native 多路 PWM、舵机波形 | 先在已确认可测试引脚验证；测 300Hz 双轮 PWM 与 50Hz 舵机；同时施加相机/CPU 负载 | 记录实际频率、占空比和抖动；原 pin 上可用，释放后可重开。没有逻辑分析仪/示波器时标记波形精度未验证，不拿 API 返回成功替代 |
| P4 逐外设迁移 | 灯→蜂鸣器→单轮→双轮→舵机→互斥逻辑；每项一个小提交 | Pi 诊断 CLI 有时限地驱动单个设备，再由旧手机发送相同指令；车轮架空验证方向与停止 | 灯开关、提示时长、轮速/方向、原地转向、舵机定位、500ms 释放和互斥逐项确认；记录机器日志及人工观察/视频 |
| P5 固定图像链路 | FixtureFrameSource + UDP 帧发送 + 摄像头命令启停 | Pi 发送已知 320×240 JPEG 给旧手机；重复打开/关闭 10 次 | 手机正确显示固定图，包格式/接收端口正确，无残留 UDP socket；不依赖实际摄像头 |
| P6 实际摄像头 | 先排查枚举，再替换为 RpicamFrameSource；若 USB 则选择 V4L2 适配器 | 摄像头枚举、保存单帧、短 MJPEG 采集，再运行 Java→UDP→手机；重复启停 10 次 | 手机上看到实时变化；连续 10 分钟记录帧数、丢帧、帧大小、CPU/RSS、采集进程退出；无摄像头时保持 BLOCKED |
| P7 集成与服务化 | systemd、配置外置、版本目录、日志和回滚 | 前台联调通过后再装服务；start/stop/restart，最后验证重启开机运行 | 旧手机完成控制+预览；运行 30 分钟无持续资源增长；停止后释放端口/相机/外设；重启恢复可连接；旧版本可回滚 |
| P8 可选识别 | 仅在明确要保留识别功能时选择 Linux ARM64 推理后端 | Pi 上固定模型/固定图结果与耗时，再接实时帧 | 模型结果、内存、延迟有实测记录；未执行则明确排除于首轮完成范围 |

P3 的频率误差/抖动阈值需依据电机驱动与舵机规格在测试前写入配置；尚无器件规格，不虚构统一容差。P6 初始目标建议 320×240、5fps，实际达不到时记录原因再确定最低可接受值，不直接宣称已达到性能目标。

P4 的人工物理观察不能由 SSH 日志替代。远程 agent 可负责部署、执行和收集结果，现场负责确认接线、车轮架空和实际动作。

## 7. 可重复验证的执行约定

统一入口（未实现的套件明确返回 BLOCKED）：

```bash
# 本机构建（使用 car_pi 独立 wrapper）
cd car_pi
./gradlew test installDist

# 部署工具接收 SSH target，不保存密码
./scripts/deploy.sh pi@<PI_IP>

# 以下在 Pi 的版本目录执行
bin/car-pi doctor --output results/doctor.json
bin/car-pi serve --config config/recording.properties

# 后续阶段逐步实现，不在 P1 批量驱动硬件
bin/car-pi verify --suite protocol --output results/protocol.json
bin/car-pi verify --suite gpio --config config/pi3.properties --output results/gpio.json
bin/car-pi verify --suite fixture-video --output results/fixture-video.json
bin/car-pi verify --suite camera --output results/camera.json
```

`verify` 非零退出表示失败或前置条件未满足；报告明确区分 PASS / FAIL / BLOCKED / MANUAL_PENDING。测试客户端不能只断言连接成功，要通过本地动作日志或验证接口关联实际执行结果；不要为测试在旧协议上添加 ACK 导致兼容变化。

每次收集：阶段、时间、Git commit、构建产物 SHA-256、OS/kernel/Java/native 版本、配置摘要、执行命令、退出码、stdout/stderr、预期/实际、人工观察和附件。原始结果存运行目录，脱敏验收摘要归档到 `doc/migration/results/`；不要提交密码。

新版本部署到 `~/thingshello/releases/<version>/`，配置与结果独立；前台验证后才切换服务。保留上一版本分发包与配置，实现可回退的版本指向。P1 默认 recording；真实 IO 必须选择已核对的硬件配置。不会在默认启动时自动跑电机验收。

每阶段结束检查 `git diff -- app/` 为空；新构建不需要 Android SDK。源自旧工程的代码保留版权和许可证说明，并记录来源路径。

## 8. 下一步

P1、P2 自动协议验收和 P5 自动固定图像验收已完成。Pi 已运行 recording + fixture 服务，P7 服务管理已验证。后续补旧手机操作与显示、30 分钟稳定性，以及接入外设后的 P3/P4/P6；不将当前模拟后端视为真实硬件迁移完成。

尚需现场确认：摄像头类型及连接状态、舵机 PWM0 对应接线、外设是否沿用原 BCM 接线、可否提供波形测量工具。当前不因这些问题阻塞 Java 与协议迁移。

## 9. 技术选型参考

- [Pi4J 版本与 Java 对应关系](https://www.pi4j.com/about/history/)
- [Pi4J PWM provider、硬件通道与软件 PWM](https://www.pi4j.com/documentation/io-types/pwm/)
- [Pi4J pigpio provider](https://www.pi4j.com/documentation/providers/pigpio/)
- [Raspberry Pi 官方摄像头工具文档](https://www.raspberrypi.com/documentation/accessories/camera-software.html)

文档提供技术路线依据，当前 Pi 的兼容性仍以阶段实测为准。
