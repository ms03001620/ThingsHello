# 排线摄像头 720p H.264 视频延迟分析与优化记录

日期：2026-10-30  
适用范围：`car_pi/deploy/csi/`（树莓派 MediaMTX RTSP 服务）与 `app_mobile` 的 `H264Activity` 独立预览页。  
前提约束：**不降低 `1280×720` 分辨率**；保持排线 H.264 链路与旧 USB JPEG/UDP 链路完全独立，不修改 `app/` 及 USB 采集代码。

---

## 1. 优化前状态与现象

在启用 Android 11+ 解码器硬件低延迟（`low-latency=1`）并将 ExoPlayer `DefaultLoadControl` 缓冲门槛下调至 `75/200/50/75 ms` 后，延迟较最初版本明显改善，但仍存在两类现象：

1. 页面稳定播放（`STATE_READY`）时，播放器内部缓冲 `getTotalBufferedDuration()` 长期停留在 **`170～240 ms`**，并未随 `bufferForPlaybackMs=50` 回落。
2. 偶发无线抖动后，画面延迟会只增不减，除非点击“重试”重新建连。

---

## 2. 根因定位（源码 + 字节码 + 实机检查）

### 2.1 根因一：ExoPlayer 2.14.2 的 RTSP 首帧时间偏移与内置追帧失效（约 150～250 ms+）

核查 `exoplayer-rtsp:2.14.2` 与 `exoplayer-core:2.14.2` 字节码确认以下机制：

1. **常驻流首包非 IDR 导致的固有 PTS 正向偏移**：
   - 车端 MediaMTX 配置 `sourceOnDemand: false` 常驻编码，之前按 `20 fps`、`rpiCameraIDRPeriod: 10`（每 `500 ms` 一个 IDR 关键帧）输出。
   - 手机发起 `PLAY` 时大概率从两个 IDR 之间的 P 帧切入。`RtpH264Reader` 以收到的**第一个 RTP 包（P 帧）**锁定基准时间戳 `firstReceivedTimestamp`（记为 `t = 0`）。
   - `SampleQueue` 在收到首个关键帧前要求 `upstreamKeyframeRequired = true`，会丢弃前序无法独立解码的 P 帧；当首个 IDR 到达时，其 `sampleTimeUs` 已处于 **`+0～450 ms`（数学期望约 `225 ms`，与实测 `170～240 ms` 完全吻合）**。
   - 此时 ExoPlayer 的 `DefaultMediaClock` 从 `0 ms` 起步，渲染器会等待媒体时钟追上该 PTS 后才渲染每一帧，导致约 `200 ms` 的实时帧始终积压在 `SampleQueue` 中。
2. **`maxBufferMs` 不限制 RTSP 推流积压**：
   - `RtspMediaPeriod.continueLoading(long)` 直接返回 `isLoading()`（恒为 `true`），网络推流会持续写入 `SampleQueue`，不受 `DefaultLoadControl` 的 `maxBufferMs=200` 截断。
3. **内置 `LivePlaybackSpeedControl` 在 RTSP 下未激活**：
   - `ExoPlayerImplInternal.shouldUseLivePlaybackSpeedControl` 要求 `window.isLive() && window.isDynamic && window.windowStartTimeMs != TIME_UNSET`。
   - 而 `RtspMediaSource` 构建 `SinglePeriodTimeline` 时固定传入 `isDynamic = false` 且 `windowStartTimeMs = TIME_UNSET`，因此内置直播自动倍速追帧**完全不生效**，任何初始偏移或网络抖动造成的积压在整个会话期间都不会自行排空。

### 2.2 根因二：树莓派无线省电休眠 + 2.4 GHz 同频干扰 + TCP 队头阻塞（20～100 ms 抖动尖峰）

实机检查树莓派 3B+（`wlan0`）无线状态发现：

1. **`Power save: on`**：`brcmfmac` 驱动默认开启 802.11 省电模式，网卡在 Beacon 周期间歇休眠，导致 RTP 数据包攒批突发与几十毫秒级抖动。
2. **2.4 GHz 信道拥塞**：小车连接在 `2462 MHz`（信道 11），现场扫描同一信道重叠了 4 个满信号 AP；而路由器提供的 `5 GHz`（信道 48，`5240 MHz`）完全空闲。
3. **RTSP over TCP 队头阻塞**：客户端开启 `setForceUseRtpTcp(true)` 后，2.4 GHz 上的单次无线丢包重传会阻塞后续所有 RTP 包按序上交；重传恢复后瞬间涌入的数据包进一步抬高播放器缓冲。

### 2.3 根因三：20 fps 采集帧周期偏大（约 40～75 ms 物理流水线延迟）

1. 原配置 `rpiCameraFPS: 20` 对应单帧周期 **`50 ms`**。传感器曝光读出 → ISP 处理 → VideoCore IV 硬件 H.264 编码 → 手机解码与 VSync 上屏按“帧数”串联推进，帧率越低，每一级流水线固有等待越长。
2. 当前选用的 OV5647 `1296:972:10:P`（2×2 binning）传感器模式原生支持最高约 `42 fps`，且树莓派硬件编码时整机 1 分钟负载仅约 `0.29`，具备在不降 `1280×720` 分辨率前提下提升帧率的充足余量。

---

## 3. 分级优化方案

### 3.1 P0：低风险高收益项（已实施）

#### （1）手机端：轻量分层倍速追帧 + 回差防抖

位置：`app_mobile/src/main/java/org/mark/mobile/preview/H264Activity.java`

- **原理**：排线预览流仅含单路 H.264 视频轨（无音频变调问题），且在 `shouldUseLivePlaybackSpeedControl == false` 时，`player.setPlaybackSpeed(float)` 可直接调整 `DefaultMediaClock` 推进速率，主动消耗 `SampleQueue` 积压。
- **策略**：在 `STATE_READY` 状态下每 `200 ms` 采样 `player.getTotalBufferedDuration()`，按阶梯控速并设置 `45～75 ms` 回差带防止频繁切换或抽空缓冲触发重缓冲（`STATE_BUFFERING`）：

| 当前播放缓冲 `bufferedMs` | 目标倍速 | 设计目的 |
| --- | --- | --- |
| `> 350 ms` | `1.25x` | 网络偶发大卡顿后快速追回实时边缘（不发 RTSP `SEEK` 避免断流） |
| `141 ～ 350 ms` | `1.15x` | 进页面约 1 秒内平滑吃掉首帧非 IDR 造成的 `~200 ms` 初始偏移 |
| `76 ～ 140 ms` | `1.05x` | 温和逼近目标低水位，肉眼无加速感 |
| `46 ～ 75 ms` | 回差保持（追帧中保持 `1.05x`，稳态保持 `1.0x`） | 避免在阈值边界来回震荡，兼容单帧（`33 ms`）正常到达波动 |
| `<= 45 ms` | `1.0x` | 保留约 1～1.5 帧安全垫，防止轻微抖动抽干队列引发 `75 ms` 重缓冲 |

- **生命周期**：仅在目标倍速变化时调用 `setPlaybackSpeed`；页面 `onStop()` 或点击“重试”调用 `releasePlayer()` 时移除定时任务并重置 `appliedSpeed = 1.0f`。

#### （2）车端：保持 `1280×720` 提升至 `30 fps` 并关闭额外 ISP 降噪

位置：`car_pi/deploy/csi/mediamtx.yml`（部署路径 `/opt/car-pi-rtsp/mediamtx.yml`）

- `rpiCameraWidth: 1280`、`rpiCameraHeight: 720`、`rpiCameraMode: "1296:972:10:P"` 保持不变；
- `rpiCameraFPS`：`20` → **`30`**（单帧间隔由 `50 ms` 缩短为 `33.3 ms`，同时首帧最大 GOP 等待由 `500 ms` 缩短为 `333 ms`）；
- `rpiCameraBitrate`：`2000000` → **`2500000`**（匹配 30 fps 维持单帧画质）；
- 新增 `rpiCameraDenoise: "off"`，关闭额外 ISP 空域/色度降噪开销。
- **室内暗光补充说明**：若环境照度极低，自动曝光可能将快门拉长至 `40～50 ms`，既增加运动拖影也会把传感器实际帧率压低回 `20 fps`；必要时可在 `mediamtx.yml` 增加 `rpiCameraShutter: 20000`（限制快门上限 `20 ms`，以画面略暗换取稳定帧率与低曝光延迟）。
- **回退方式**：树莓派保留备份 `/opt/car-pi-rtsp/mediamtx-before-p0-30fps.yml`，覆盖回 `/opt/car-pi-rtsp/mediamtx.yml` 并执行 `sudo systemctl restart car-pi-rtsp` 即可回退。

#### （3）车端：关闭无线网卡省电模式并持久化

在树莓派上同时执行运行态关闭与 NetworkManager 配置持久化：

```bash
# 立即关闭当前无线省电
sudo /usr/sbin/iw dev wlan0 set power_save off

# 持久化到当前 Wi-Fi 连接配置（2 = disable），重启后继续生效
sudo nmcli connection modify "<WIFI_CONN_NAME>" 802-11-wireless.powersave 2

# 核验状态
/usr/sbin/iw dev wlan0 get power_save
```

---

### 3.2 P1：传输层进阶优化（待按需启用）

1. **切换至路由器 `5 GHz` 频段**：
   - Pi 3B+ 原生支持 `5 GHz` 802.11ac。将小车与手机接入干净的 `5 GHz` SSID 后，空口重传与同频退避显著减少（切换新连接后同样需设置 `802-11-wireless.powersave 2`）。
2. **评估 `RTSP over UDP`（消除 TCP 队头阻塞）**：
   - 车端 MediaMTX 已监听 UDP `:8002`（RTP）/ `:8003`（RTCP）。在 `5 GHz` + 关闭省电前提下，可将 `H264Activity` 的 `.setForceUseRtpTcp(true)` 改为 `false`（ExoPlayer 默认优先尝试 UDP，不通时自动回退 TCP；包序连续时 `RtpPacketReorderingQueue` 零等待）。
   - 权衡：UDP 无队头阻塞，但若发生真实丢包，在下一个 IDR 前会出现短暂局部花屏，且乱序缺包时重排队列最长等待约 `100 ms`。
3. **MediaMTX 原生 WebRTC 对照测试**：
   - 将 `mediamtx.yml` 中 `webrtc: false` 改为 `true`（直接复用同一路 `1280×720` 硬件 H.264 编码流，零额外编码负载）。
   - WebRTC 支持连入瞬间发送 PLI 请求关键帧及基于 UDP 的 NACK 重传，可通过手机浏览器访问 `http://<小车IP>:8889/csi` 直接观测当前硬件链路的延迟下限。

---

### 3.3 P2：极致低延迟备选（FPV 级 `< 80 ms` 架构方向）

若后续遥控对延迟有极致要求（完全消除播放器层面的 1～2 帧保底缓冲）：

- **原因**：ExoPlayer 围绕 `SampleQueue` + `MediaClock` 时间戳同步设计，始终以平滑回放为优先。
- **方案**：在 `H264Activity` 中绕过 ExoPlayer，使用轻量客户端直接接收 RTP/H.264 NALU 拼帧后送入开启 `KEY_LOW_LATENCY=1` 的 Android 原生 `MediaCodec`，并在 `dequeueOutputBuffer` 返回时忽略 PTS 立即调用 `releaseOutputBuffer(index, true)` 渲染至 `SurfaceView`，可将手机端缓冲严格压缩至 **0 帧排队**。

---

## 4. 附：旧 USB 摄像头链路（320×240 MJPEG/UDP）已知延迟点备忘

按仓库约定，旧 USB 链路保持原样未修改；若后续单独排查 USB 模式延迟，可关注以下三处：

1. `car_pi/.../camera/CameraController.java`：发送循环固定 `Thread.sleep(200)`，主动限制在约 `5 fps`（单帧采样间隔最高 `200 ms`）。
2. `app_mobile/.../connect/udp/UdpReceiver.java`：`start()` 后固定 `Thread.sleep(1000)` 再发送 `"hello"` 握手包，增加首屏等待 `1 s`。
3. `app_mobile/.../preview/PreviewPresenter.java`：每收到一包 UDP 都向 `WorkThreadHandler` 串行队列 `post` 解码绘制任务，若主界面/画布锁竞争变慢时未丢弃队列中的过时帧。

---

## 5. P0 实施验证记录

| 检查项 | 状态 | 验证方式与结果 |
| --- | --- | --- |
| 车端 `1280×720 @ 30fps` 硬件编码启动 | **已验证** | `car-pi-rtsp` 为 `active`；日志确认 `1280x720-YUV420` + `1296x972` RAW + `using hardware H264 encoder` |
| 局域网实流输出帧率 | **已验证** | 本机 RTSP/TCP 实拉采样 `2.50 s` 收到 75 个完整标记帧，实测约 **`29.6 fps`** |
| 车端无线省电关闭与持久化 | **已验证** | `iw dev wlan0 get power_save` 返回 `Power save: off`；`nmcli` 回读 `powersave=disable` |
| 手机端 APK 构建与覆盖安装 | **已验证** | JDK 17 `:app_mobile:assembleDebug` 成功，`adb install -r` 覆盖安装 Pixel 10 成功 |
| 手机现场缓冲回落值、流畅度与操控体感 | **待现场验证** | 需结合页面顶部信息栏（近 5 秒渲染 FPS 与播放缓冲 ms）及实车遥控确认 |
