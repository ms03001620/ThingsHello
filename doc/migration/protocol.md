# Pi 协议迁移合同

TCP 头为正文 UTF-8 字节数（4 字节小端）+ 类型（1 字节）+ 正文。上限保持 500 KiB，截断/非法长度关闭连接；JSON/参数错误记录后仍可处理后续合法命令。不新增 ACK。

样例：`00 00 00 00` 是非法长度；文本“你好”类型 -1 编码头为 `06 00 00 00 FF`。类型按有符号 byte 解码，0xFF 为 -1。

类型和字段：0 left/right；1 red/green/blue；2 di；3 action/width/height/transferVideo/tfCmd；4 progress；5 摄像头信息；6 rotate/speed。固定命令样本位于 `car_pi/acceptance/fixtures/commands.json`。

灯有任一分量大于 0 即三色全开 100%。左转为 left=-speed、right=speed；右转相反；这是旧 WheelAction/forwardBuff 的意图，实际极性尚未验证。舵机 0..100 对应 0.8..1.5ms，每次指令重置 500ms 释放；切换至车轮取消舵机定时器。

摄像头信息源为旧 `CameraAction` → `CameraUtils.CameraInfo`。Gson 默认反射 Android Size 字段，形状为 `{"sizes":[{"mWidth":320,"mHeight":240}],"hardwareLevel":null}`；新端不伪称 Camera2 等级。disabled 模式 sizes 为空；fixture 模式仅报告 320×240。旧手机的 PreviewPresenter 只处理 -1 文本响应，最终手机兼容性仍需手动验收。

UDP 握手为 `05 68 65 6c 6c 6f`；图像包为 `05` + 完整 JPEG，回复实际来源端口；最大 JPEG 65506 字节。未实现手机未知的分片协议。

已有问题边界：旧 Wheel/Buzzer 对 setDirection 和有效电平常量的用法有疑点，本轮只记录语义，不据此推导真实电平。断连停车等业务行为不在本轮顺带修改。真实 IO 尚未实现，默认 recording 无物理风险。
