# 舵机手机控制集成验证（2026-10-30）

## 结果

已完成并部署 `0.5.2-servo` 到 `192.168.8.170:8010`。用户先确认手机灯、视频、车轮可用，修复舵机后确认“舵机也可以了”。手机四项真实功能均已验证。

## 原因与修复

上版 lgpio 仅接入车轮、灯、蜂鸣器，`servo.enabled=false`。这台车的舵机需要 BCM18 的硬件 PWM，不能简单开启 lgpio 软件舵机输出。

本版保留 lgpio 车轮路径，在同一受管 worker 中为 BCM18 单独调用内核 sysfs PWM0；不申请该 GPIO，避免破坏 ALT5。类型 4 的 progress 0/50/100 分别映射到 800/1150/1500 us，周期 20000 us。保留车轮互斥、500 ms 自动释放、断连和退出关闭。启动时先禁用输出并初始化周期；超范围脉宽拒绝。

已配置持久 overlay 和 root 准备服务，准备后仅允许 gpio 组访问 PWM 属性，主程序仍以 pi 用户运行。准备服务当前已运行成功，尚未做重启验收。

## 验证

| 项目 | 结果 |
| --- | --- |
| 23 项 JUnit 测试，包括 sysfs 映射、超范围、自动释放和断连 | 已验证 |
| Pi 协议及固定图回归 | 已验证 |
| 真实 8010 指令 progress 0/50/100，sysfs 周期/脉宽回读 | 已验证 |
| 每次输出 650 ms 后 enable=0；断连后 enable=0 | 已验证 |
| app_mobile 滑块控制舵机实际转动 | 已验证 |
| 灯、USB 视频、车轮手机操作 | 已验证 |
| 持久 PWM 配置重启后恢复 | 未验证 |
| 仪器测量实际 PWM 频率与抖动 | 未验证 |

最终全量 23 项测试通过。期间原有 `CameraCaptureTest.asynchronousFailureKeepsCaptureDiagnosticAndAllowsReopen` 曾因 3 秒内未取得异步错误信息失败一次；未修改摄像头代码，重跑通过，保留为测试时序稳定性观察项。

回读数据不等同于仪器测量，见 [原始验收数据](2026-10-30-servo-integration.json)。未修改旧 `app/`、`app_mobile/` 或通信库。

## 回退

旧配置在 `/home/pi/thingshello/config/service-before-servo-2026-10-30.properties`；旧版本为 `0.5.1-control`。回退时恢复旧配置、切换旧发布目录；关闭 PWM 输出并移除 `car-pi.service.d/servo-pwm.conf` 后 daemon-reload。启动配置备份为 `/boot/firmware/config.txt.before-car-servo`。
