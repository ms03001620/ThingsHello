# CSI H.264 RTSP

独立 MediaMTX 1.21.3 服务，保留 Java USB 配置。地址 `rtsp://小车IP:8554/csi`。按需启用 OV5647，最后读者离开 1 秒后停止。640x480/20fps、1Mbps、硬件 H.264；双翻转纠正实拍倒置。

从官方 release 下载 `mediamtx_v1.21.3_linux_arm64.tar.gz`（64 位 Pi OS），解压到 `/opt/car-pi-rtsp`，将本目录 mediamtx.yml 放入该目录。安装：

```sh
sudo cp car-pi-rtsp.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now car-pi-rtsp
journalctl -u car-pi-rtsp -f
```

需要系统 libcamera 支持、pi 的 video/render 组权限。端口：RTSP TCP8554，RTP/RTCP UDP8002/8003，避免旧 UDP8000。服务仅用于可信局域网。两种摄像头不会同时连接到板子。此服务和 H.264 页面独立管理排线视频，不新增 USB 启停、恢复或切换逻辑。

手机使用 Android 原生 MediaPlayer/VideoView RTSP 解码，无新增依赖。不同 Android 厂商的 RTSP 兼容性需现场验收。页面重试重新连接；离开页面停止播放。

参考：https://mediamtx.org/docs/references/configuration-file

进度与下一步：doc/migration/csi-h264-plan.md。
