# 2026-10-30 手机能连接但车轮不动：定位与修复

## 根因与旧 app 的区别

手机协议没有丢失。Pi 的 `actions.jsonl` 中能看到手机发来的类型 0、`left/right`、正负速度及归零指令，随后也有 `wheel.speed`；但这些记录全部标记 `backend=recording`。当时服务外置配置确实为 `backend=recording`，所以只录制动作，不输出引脚。摄像头使用独立的 V4L2 真实采集链路，因此仍能看到画面。

旧 `app/ctrl/wheel/WheelAction` 收到指令后调用 `Wheel` 的 GPIO/PWM；迁移版当时调用的是 RecordingHardware。不是手机发错 JSON，也不是 TCP 8010 断开，而是部署尚未切换到真实外设后端。

| 对照项 | 旧 app | 本次修复 |
| --- | --- | --- |
| 通信 | 类型 0 的 left/right；类型 6 的 rotate/speed | 保持不变，未修改 app_mobile |
| 引脚 | 左 21/20/16，右 26/19/13 | 保持原 BCM 引脚；正向 20/19 高电平 |
| PWM | Android SoftPwm，配置 300Hz | liblgpio 原生软件定时 PWM，配置 300Hz；实际波形精度未用仪器测量 |
| 动作去向 | Android Things 真实 GPIO | 从 recording 改为 lgpio 真实 GPIO |
| 同向变速 | 更新占空比 | 同向只更新占空比，换向先停 PWM，避免连续指令反复停顿 |
| 断开控制连接 | 未在本次修改旧代码 | 释放车轮，避免真实输出停留在最后一条运动指令 |

## 实现与验证

- 新增受 Java 管理的 lgpio worker；PWM 波形由 native liblgpio 产生，Python/Java 不用 sleep 循环生成 PWM。worker EOF/TERM 会关闭输出和 GPIO 句柄，不依赖 pigpiod。
- 系统 lgpio 0.2.2.0 在没有活动 PWM 时直接发停止脉冲会报 `bad PWM micros`；已先检查 `tx_busy`，只对活动 PWM 执行停止，并加入回归测试。
- 抽出共用 `LinuxHardware`，保留实验性 pigpio 传输；新增 lgpio 传输，明确记录 `pwm.configuredHz` 而不是假装测到了实际频率。
- 22 项 JUnit 测试通过；Pi 上协议与固定图回归通过。
- 独立 18012 端口真实测试：左轮 40%、右轮 40%、反向运动后断连、运动中 SIGTERM；归零/断连/退出后 BCM16 与 BCM13 都读回低电平，退出 143，无 rejected。报告见 `car_pi/results/control-fix/control-test.json` 及下方提交的证据。
- 这证明控制到真实 GPIO 的链路和停止路径已通过机器验收；最终手机实际轮动体验仍需现场确认，不能仅根据日志宣布所有物理方向和速度均正确。

## 部署

- 当前 `0.5.1-control`，配置 `backend=lgpio`、`wheel.enabled=true`，真实灯/蜂鸣器同时启用，摄像头继续 V4L2。
- systemd 使用 `KillMode=mixed`，由 Java 先有序关闭工作子进程；启动 `active`、`Result=success`、`NRestarts=0`。
- 舵机仍未接入这个 lgpio 后端，继续保留文档中独立的 sysfs 硬件 PWM 路径；未谎称全车迁移已结束。
- 旧配置备份为 `~/thingshello/config/service-before-lgpio-2026-10-30.properties`。回退到旧版本时先恢复此配置，避免旧版本不认识 lgpio。
- 分发包 SHA-256：`464b1e63cbff5ba521fa407bea151734f145e2cc6a0537c9d2d8d14550f1cd94`。

| 验收项 | 状态 |
| --- | --- |
| 手机原有指令正确到达、根因定位 | 已验证 |
| lgpio 配置 300Hz，驱动调用与停止流程 | 已验证 |
| 指令归零、断连、SIGTERM 后使能低电平 | 已验证 |
| 原手机实际轮动 / 松手停止 | 未验证（等待现场确认） |
| 实际频率误差和抖动、轮速标定 | 未验证 |
