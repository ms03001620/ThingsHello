# 排线摄像头 H.264 / RTSP 实施与续接计划

约定：USB JPEG/UDP 方案保留；新增排线 H.264 RTSP；主界面 h264 按钮进入独立只读预览。实现完成与验证分开记录。

| 步骤 | 工作 | 完成 | 验证 |
| --- | --- | --- | --- |
| 1 | 检查现有入口、确定协议和生命周期 | 已完成 | 已验证（源码检查） |
| 2 | Pi RTSP 服务、常驻采集和部署文件 | 已完成 | 已验证 |
| 3 | 手机 h264 入口、独立播放器页面、生命周期 | 已完成 | 已验证（用户已看到画面；生命周期全场景未验证） |
| 4 | 构建检查、部署说明、导航更新 | 已完成 | 已验证（JDK17 assembleDebug 通过） |
| 5 | Pi RTSP 实流及手机现场预览 | 已完成 | 已验证（延迟仍待优化） |

续接：步骤 2/3 代码已完成，步骤 4 完成；步骤 5 已通过 RTSP DESCRIBE/硬件编码启动及无人读取停止检查，下一步安装 APK 做手机预览验收。设备 pi@192.168.8.229，排线 OV5647，rpicam-still 拍照已验证。旧服务仍使用 USB；不得因 USB 缺席更改其配置。计划使用 MediaMTX 的原生 rpiCamera 源按需输出 H.264，端口 8554、路径 csi；手机使用系统 RTSP 播放器以避免升级现有 Android 构建依赖。停止页面即释放播放器，源无读者后停止。H.264 页面仅管理自己的 RTSP 播放器，不新增 USB 启停、恢复或摄像头切换逻辑。两种摄像头不会同时连接到板子。

用户修正：USB 与排线不会同时接板。禁止新增两者暂停/恢复/切换联动；H.264 按钮仅打开独立页面。已删除此前错误的恢复设计描述；原 USB 实现未修改。

## 当前证据与续接入口

- Pi 已安装 MediaMTX v1.21.3 到 `/opt/car-pi-rtsp`，服务 `car-pi-rtsp` enabled/active。
- `rtsp://192.168.8.229:8554/csi` DESCRIBE 返回 200、`H264/90000`；日志确认 `using hardware H264 encoder`，无人读取 1 秒后 source stopped。
- 手机 APK 构建使用 JDK17；默认 JDK23 会导致旧 AGP dex 失败。命令：`JAVA_HOME=/Users/mazhenjin/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home ./gradlew :app_mobile:assembleDebug`。
- 下一步：安装 `app_mobile/build/outputs/apk/debug/app_mobile-debug.apk`，连接 192.168.8.229:8010，点击 h264；确认画面/返回/重试。手机解码和持续视频仍未验证，不能因 DESCRIBE 成功标记全部实测通过。
- 原 USB 页面、采集、协议均未修改。H264Activity 仅依赖当前连接 host，并单独管理 RTSP 播放器。

## App 安装后的车端部署核验

用户已安装 APK。已核对 192.168.8.229：Java car-pi 0.5.2-servo 与独立 car-pi-rtsp 均 active，RTSP 配置与仓库一致。局域网完成 DESCRIBE → SETUP（TCP interleaved）→ PLAY，收到 4206 字节 RTP 数据：已验证。H.264 本次无 Java 控制程序改动，因此沿用现有 Java 发布，新增功能由已部署的独立 RTSP 服务提供。手机实际显示仍未验证。

## 手机连接失败修复（进行中）

手机日志对应 RTSP 连接后 EOF、未 SETUP。发现按需首次 DESCRIBE 缺少 sprop-parameter-sets；已将源改为常驻编码（服务停机释放），再次 DESCRIBE 含 SPS/PPS 和 profile-level-id。用户正在重试原 App。另将手机播放器改为 ExoPlayer 2.14.2 RTSP/TCP，APK 构建已通过；未确认手机画面前仍为未验证。此改动独立于 USB。

## 延迟优化

用户确认原 App 已显示排线画面：手机预览已验证，但报告延迟高。新增 ExoPlayer 低缓冲设置（150..500ms，起播100ms、重缓冲150ms）；车端 baseline profile、每10帧关键帧。需安装新版 APK；真实端到端延迟未测量，优化效果未验证。USB 无改动。

## 固定 1–2 秒延迟：解码器低延迟模式

用户确认新版播放器仍有约 1–2 秒固定延迟。Pi 负载较低、硬件 baseline H.264 正常；手机运行 ExoPlayer 2.14.2，使用 c2.google.avc.decoder。设备 vendor codec 配置明确支持 low-latency，之前配置日志未启用该项。RTP 重排等待源码检查为30ms，不是已知的1–2秒固定等待；尚不能断定全部延迟来源。

- [x] CSI 独立 renderer：API30+ 且当前 decoder 支持时设置 low-latency=1；不支持则保留默认设置。已完成，构建已验证。
- [x] 每2秒记录播放器 bufferedMs/state，离开页面取消诊断。已完成，构建已验证。
- [x] 手机运行时确认 lowLatency=true，用户反馈“延迟明显降低了”：已验证；端到端毫秒数未测量。

下一步：安装本轮 APK 后打开 h264，通过 `adb logcat -s CsiLatency` 检查解码器和缓冲日志；用户对比固定延迟变化。bufferedMs 仅播放器缓冲，不等于端到端延迟。不改变 USB 或车端编码参数，以便比较单个变量。

## 后续小幅压缩播放器缓冲

日志确认 Pixel 10 lowLatency=true；连续 READY 样本 bufferedMs 约142..243ms，大部分约170..240ms。用户确认延迟明显降低。保持此版本 `9a2b98c` 作为可回退基线。

本轮只改 LoadControl：min/max 75/200ms，起播50ms，重新缓冲75ms（原150/500/100/150）。这些是加载门槛，不是对总延迟的硬性上限，也不主动跳帧清理已积压数据。预计仅小幅优化；若增加卡顿则回退原门槛。车端帧率、码率、TCP及解码器低延迟模式不变。

- [x] 降低缓冲门槛代码：已完成。
- [x] JDK17 assembleDebug、ADB覆盖安装Pixel 10：已验证。
- [ ] 新门槛的现场延迟与卡顿对比：未验证。
