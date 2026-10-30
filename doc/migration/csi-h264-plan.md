# 排线摄像头 H.264 / RTSP 实施与续接计划

约定：USB JPEG/UDP 方案保留；新增排线 H.264 RTSP；主界面 h264 按钮进入独立只读预览。实现完成与验证分开记录。

| 步骤 | 工作 | 完成 | 验证 |
| --- | --- | --- | --- |
| 1 | 检查现有入口、确定协议和生命周期 | 已完成 | 已验证（源码检查） |
| 2 | Pi RTSP 服务、按需采集和部署文件 | 已完成 | 未验证 |
| 3 | 手机 h264 入口、独立播放器页面、生命周期 | 已完成 | 未验证 |
| 4 | 构建检查、部署说明、导航更新 | 已完成 | 已验证（JDK17 assembleDebug 通过） |
| 5 | Pi RTSP 实流及手机现场预览 | 未完成 | 未验证 |

续接：步骤 2/3 代码已完成，步骤 4 完成；步骤 5 已通过 RTSP DESCRIBE/硬件编码启动及无人读取停止检查，下一步安装 APK 做手机预览验收。设备 pi@192.168.8.229，排线 OV5647，rpicam-still 拍照已验证。旧服务仍使用 USB；不得因 USB 缺席更改其配置。计划使用 MediaMTX 的原生 rpiCamera 源按需输出 H.264，端口 8554、路径 csi；手机使用系统 RTSP 播放器以避免升级现有 Android 构建依赖。停止页面即释放播放器，源无读者后停止。H.264 页面仅管理自己的 RTSP 播放器，不新增 USB 启停、恢复或摄像头切换逻辑。两种摄像头不会同时连接到板子。

用户修正：USB 与排线不会同时接板。禁止新增两者暂停/恢复/切换联动；H.264 按钮仅打开独立页面。已删除此前错误的恢复设计描述；原 USB 实现未修改。

## 当前证据与续接入口

- Pi 已安装 MediaMTX v1.21.3 到 `/opt/car-pi-rtsp`，服务 `car-pi-rtsp` enabled/active。
- `rtsp://192.168.8.229:8554/csi` DESCRIBE 返回 200、`H264/90000`；日志确认 `using hardware H264 encoder`，无人读取 1 秒后 source stopped。
- 手机 APK 构建使用 JDK17；默认 JDK23 会导致旧 AGP dex 失败。命令：`JAVA_HOME=/Users/mazhenjin/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home ./gradlew :app_mobile:assembleDebug`。
- 下一步：安装 `app_mobile/build/outputs/apk/debug/app_mobile-debug.apk`，连接 192.168.8.229:8010，点击 h264；确认画面/返回/重试。手机解码和持续视频仍未验证，不能因 DESCRIBE 成功标记全部实测通过。
- 原 USB 页面、采集、协议均未修改。H264Activity 仅依赖当前连接 host，并单独管理 RTSP 播放器。
