# CSI H.264 RTSP

独立 MediaMTX 1.21.3 服务，是小车唯一的视频通路（car_pi 不处理视频）。地址 `rtsp://小车IP:8554/csi`。常驻启用 OV5647，以确保首次 DESCRIBE 已具备 SPS/PPS；停止服务时释放摄像头。1280x720/30fps、2.5Mbps、关闭 ISP 降噪、硬件 H.264；双翻转纠正实拍倒置。

从官方 release 下载 `mediamtx_v1.21.3_linux_arm64.tar.gz`（64 位 Pi OS），解压到 `/opt/car-pi-rtsp`，将本目录 mediamtx.yml 放入该目录。安装：

```sh
sudo cp car-pi-rtsp.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now car-pi-rtsp
journalctl -u car-pi-rtsp -f
```

需要系统 libcamera 支持、pi 的 video/render 组权限。端口：RTSP TCP8554，RTP/RTCP UDP8002/8003。服务仅用于可信局域网。此服务独立管理排线摄像头视频，手机端用 ExoPlayer 拉取 RTSP。

手机使用 ExoPlayer RTSP 模块，强制 RTP over TCP。不同 Android 厂商的 RTSP 兼容性需现场验收。页面重试重新连接；离开页面停止播放。

参考：https://mediamtx.org/docs/references/configuration-file

720p 延迟分析与优化方案见仓库根目录 `doc/csi-h264-latency-optimization.md`。

720p 显式选择1296×972传感器模式，避免自动选择1080p模式造成取景变窄；输出仍为1280×720，16:9会裁剪部分上下视野。
