# `car_pi` 打包、部署与运维命令手册

本文档详细说明 `car_pi` 工程从本地构建打包、上传发布、树莓派首次安装、版本原子切换与回滚，到日常 `systemd` 服务运维及硬件排障的**全部命令与参数用法**。

---

## 1. 部署目录与文件说明

### 仓库内相关文件

| 路径 | 作用 |
| --- | --- |
| `deploy/car-pi.service.in` | 主服务 `systemd` 单元模板（含 `@USER@`、`@ROOT@` 占位符） |
| `deploy/prepare-servo-pwm.sh` | 开机以 `root` 导出 BCM18 硬件 `PWM0` 并授权给 `gpio` 组的初始化脚本 |
| `deploy/car-pi-servo-pwm.service` | 在 `car-pi.service` 启动前自动运行上述脚本的一次性（`oneshot`）服务 |
| `deploy/servo-pwm.conf` | `car-pi.service` 的 Drop-in 依赖声明（绑定 `Requires` / `After` 舵机 PWM 服务） |
| `deploy/csi/` | 排线摄像头 MediaMTX H.264 RTSP 独立服务配置（详见 [csi/README.md](csi/README.md)） |
| `scripts/deploy.sh` | 开发机端一键测试、打包、SCP 上传并在树莓派运行环境自检 |
| `scripts/activate.sh` | 树莓派端预检、软链接原子切换版本、重启服务及失败自动回滚 |

### 树莓派目标目录结构（以 `/home/pi/thingshello` 为例）

```text
/home/pi/thingshello/
├── config/
│   └── service.properties          # 生产环境实际生效的配置文件（不随版本升级覆盖）
├── releases/
│   ├── car-pi-0.1.0/               # 历史解压版本
│   └── car-pi-0.1.1/               # 新上传解压版本
├── current -> releases/car-pi-0.1.1 # 当前运行版本软链接（systemd 工作目录指向此处）
└── previous -> releases/car-pi-0.1.0 # 上一可用版本软链接（由 activate.sh 自动维护）
```

---

## 2. 前置环境准备命令

### 2.1 开发机（Mac / Linux）

需安装 **JDK 21 及以上版本**：

```bash
java -version
```

### 2.2 树莓派（Raspberry Pi OS Lite 64-bit）

1. **安装运行依赖**（Java 21 JRE、`lgpio` Python 绑定、V4L2 工具）：
   ```bash
   sudo apt update
   sudo apt install -y openjdk-21-jre-headless python3-lgpio v4l-utils
   ```

2. **配置运行用户硬件权限组**（假设运行用户为 `pi`，修改后需重新登录生效）：
   ```bash
   sudo usermod -aG gpio,video,render pi
   ```

3. **开启 BCM18 硬件 PWM0**（摄像头云台舵机必需）：
   在 `/boot/firmware/config.txt` 末尾添加配置并重启树莓派（启用后板载 3.5mm 模拟音频不可用）：
   ```bash
   grep -qxF 'dtoverlay=pwm,pin=18,func=2' /boot/firmware/config.txt \
     || echo 'dtoverlay=pwm,pin=18,func=2' | sudo tee -a /boot/firmware/config.txt
   sudo reboot
   ```

4. **关闭 Wi-Fi 网卡省电休眠**（降低控制与视频抖动，推荐执行）：
   ```bash
   # 立即关闭当前无线省电
   sudo /usr/sbin/iw dev wlan0 set power_save off

   # 查看当前 Wi-Fi 连接名称并持久化关闭省电（2 = disable）
   nmcli connection show --active
   sudo nmcli connection modify "<WIFI连接名称>" 802-11-wireless.powersave 2

   # 验证（应输出 Power save: off）
   /usr/sbin/iw dev wlan0 get power_save
   ```

---

## 3. 本地构建与打包命令（Gradle）

所有构建命令均在 `car_pi` 目录下执行：

```bash
cd car_pi
```

### 3.1 运行全部单元测试

```bash
./gradlew test
```

### 3.2 生成本地可直接运行的分发目录

```bash
# 默认版本号为 0.1.0-dev
./gradlew installDist

# 指定发布版本号
./gradlew installDist -PreleaseVersion=0.1.1
```

生成产物位于 `build/install/car-pi/`，其中可执行入口为 `build/install/car-pi/bin/car-pi`，同时包含 `config/`、`deploy/`、`scripts/`。

### 3.3 打包归档文件（`.tar` / `.zip`）

```bash
# 打出 tar 包：build/distributions/car-pi-0.1.1.tar
./gradlew distTar -PreleaseVersion=0.1.1

# 打出 zip 包：build/distributions/car-pi-0.1.1.zip
./gradlew distZip -PreleaseVersion=0.1.1

# 一步完成测试 + 本地分发目录 + tar 归档包
./gradlew test installDist distTar -PreleaseVersion=0.1.1
```

---

## 4. `bin/car-pi` 命令行程序完整用法

构建或解压后，主程序入口为 `bin/car-pi`（开发机本地路径为 `./build/install/car-pi/bin/car-pi`，树莓派激活后路径为 `~/thingshello/current/bin/car-pi`）。

### 4.1 语法总览

```text
car-pi <doctor | serve | verify> [--config <path>] [--suite <name>] [--output <path>]
```

**通用参数规则：**
- 所有选项必须以 `--key value` 成对传入（不支持 `--key=value` 单参数写法）。
- 仅支持以下三个选项：`--config`、`--suite`、`--output`；传入其他选项会抛出 `IllegalArgumentException`。

| 参数 | 适用子命令 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `--config <path>` | `doctor`、`serve`、`verify`（`camera` 套件） | 内置录制模式默认配置 | 指定 `.properties` 运行配置文件路径 |
| `--suite <name>` | `verify`（必填） | 空字符串 | 指定验证套件：`protocol`、`fixture-video` 或 `camera` |
| `--output <path>` | `doctor`、`verify` | 不写文件（仅打印到标准输出） | 将格式化 JSON 结果同时写入指定文件（自动创建父目录） |

---

### 4.2 `doctor`：运行环境与配置自检

读取系统环境（OS、内核、CPU 架构、Java 版本、程序实现版本）并校验指定配置文件格式是否合法，输出 JSON 报告。不会实际驱动电机或打开摄像头。

```bash
# 使用默认配置自检并打印到终端
bin/car-pi doctor

# 校验树莓派硬件配置文件，并将结果保存到 results/doctor.json
bin/car-pi doctor --config config/pi3-lgpio.properties --output results/doctor.json
```

---

### 4.3 `serve`：启动小车控制与视频服务

加载配置，初始化 GPIO/PWM 后端与摄像头控制器，监听 TCP 控制端口（默认 `8010`）及摄像头 UDP 端口（默认 `8000`）。

```bash
# 树莓派真实硬件运行
bin/car-pi serve --config config/pi3-lgpio.properties

# 开发机本地无硬件模拟运行（使用内置测试图作为摄像头画面）
bin/car-pi serve --config config/fixture.properties

# 仅记录硬件指令到 JSONL 文件，不操作真实 GPIO
bin/car-pi serve --config config/recording.properties
```

- **启动就绪标志**：标准输出打印 `READY tcp=8010 backend=lgpio`。
- **优雅停止**：前台按 `Ctrl + C`（`SIGINT`）或后台执行 `kill <PID>`（`SIGTERM`）。Shutdown Hook 会自动关闭 Socket、停止摄像头采集、将车轮/灯/蜂鸣器复位并关闭舵机 PWM0。`SIGTERM` 退出码为 `143`（已在 `car-pi.service` 中配置 `SuccessExitStatus=143` 视为正常停止）。

**内置配置文件对比（`config/` 目录）：**

| 配置文件 | `backend` | `camera` | 适用场景 |
| --- | --- | --- | --- |
| `pi3-lgpio.properties` | `lgpio` | `v4l2` | **树莓派实车生产配置**（车轮、RGB 灯、蜂鸣器、BCM18 硬件 PWM 舵机、USB 摄像头） |
| `usb.properties` | `recording` | `v4l2` | 单独测试 `/dev/video0` USB V4L2 摄像头采集，不驱动外设引脚 |
| `rpicam.properties` | `recording` | `rpicam` | 单独测试 `rpicam-vid` MJPEG 管道采集 |
| `fixture.properties` | `recording` | `fixture` | 本地联调手机 App：无须硬件与摄像头，回传内置 `320x240` 测试 JPEG |
| `recording.properties` | `recording` | `disabled` | 仅监听 TCP 控制指令并将硬件动作写入 `results/actions.jsonl` |

---

### 4.4 `verify`：执行内置自动化验证套件

用于在部署激活前或排障时验证协议、模拟视频链路及实机摄像头单帧抓取。
- 当结果 `status` 为 `"PASS"` 时，进程退出码为 `0`；
- 当结果为 `"FAIL"` 或 `"BLOCKED"` 时，进程退出码为 **`2`**。

```bash
# 1. 验证 TCP 协议编解码与指令分发逻辑（无须硬件）
bin/car-pi verify --suite protocol --output results/verify-protocol.json

# 2. 验证模拟摄像头 UDP 视频握手与帧回传链路（无须硬件）
bin/car-pi verify --suite fixture-video --output results/verify-video.json

# 3. 验证真实摄像头能否成功抓取一帧 320x240 JPEG（需在连接了摄像头的树莓派上运行）
bin/car-pi verify --suite camera --config config/pi3-lgpio.properties --output results/verify-camera.json
```

---

## 5. 一键打包上传与版本切换脚本详解

### 5.1 开发机端上传脚本：`scripts/deploy.sh`

**语法：**

```bash
./scripts/deploy.sh <user@host> [version]
```

**参数说明：**
- `<user@host>`（必填）：树莓派 SSH 目标地址，如 `pi@192.168.1.50` 或 `pi@raspberrypi.local`（仅允许字母、数字及 `_.@:-`）。
- `[version]`（选填）：发布版本号，默认值为 `0.1.0-dev`（仅允许字母、数字及 `_.-`）。**注意：树莓派 `~/thingshello/releases/` 下不能已存在同名版本目录，否则脚本会拒绝覆盖并报错退出。**

**内部自动执行的完整流程：**
1. 在 `car_pi` 目录运行 `./gradlew test installDist distTar -PreleaseVersion=<version>`；
2. 通过 SSH 在远端创建 `~/thingshello/releases` 并检查 `car-pi-<version>` 不存在；
3. 通过 `scp` 将 `build/distributions/car-pi-<version>.tar` 上传至远端 `~/thingshello/releases/`；
4. 在远端解压归档包，并进入解压目录运行 `bin/car-pi doctor --output results/doctor.json`。

**使用示例：**

```bash
cd car_pi
./scripts/deploy.sh pi@192.168.1.50 0.1.1
```

---

### 5.2 树莓派端激活与自动回滚脚本：`scripts/activate.sh`

> **前提**：树莓派上已完成第 6 节的首次 `systemd` 服务安装，且新版本已解压在 `<root>/releases/<release-name>`。

**语法：**

```bash
<root>/releases/<release-name>/scripts/activate.sh <absolute-root> <release-name>
```

**参数说明：**
- `<absolute-root>`（必填）：部署根目录的**绝对路径**（必须以 `/` 开头，例如 `/home/pi/thingshello`，不能用 `~` 缩写）。
- `<release-name>`（必填）：`releases/` 下的版本目录名，例如 `car-pi-0.1.1`。

**内部自动执行的完整流程：**
1. 进入新版本目录，依次执行 `doctor`、`verify --suite protocol`、`verify --suite fixture-video` 预检（任一失败立即中止，不影响当前线上服务）；
2. 创建临时软链接 `<root>/current.next` 指向新版本；
3. 停止当前 `car-pi.service`，通过 `mv -Tf` 原子替换 `<root>/current` 软链接；
4. 启动 `car-pi.service`，等待 2 秒后通过 `systemctl is-active --quiet car-pi.service` 检查存活：
   - **启动成功**：将旧版本记录到 `<root>/previous` 软链接；
   - **启动失败**：自动停止异常进程，将 `<root>/current` 切回旧版本并重新启动旧版服务，最后以状态码 `1` 退出。

**使用示例（在树莓派上执行）：**

```bash
/home/pi/thingshello/releases/car-pi-0.1.1/scripts/activate.sh /home/pi/thingshello car-pi-0.1.1
```

**手动回滚到上一版本（如需人工回退）：**

```bash
PREV=$(basename "$(readlink /home/pi/thingshello/previous)")
/home/pi/thingshello/releases/"$PREV"/scripts/activate.sh /home/pi/thingshello "$PREV"
```

---

## 6. 树莓派首次安装 `systemd` 服务完整流程

新树莓派第一次部署时，先在开发机执行 `./scripts/deploy.sh pi@<小车IP> 0.1.0` 上传首个版本包，然后 SSH 登录树莓派依次执行以下 4 步：

### 步骤 1：初始化运行配置目录

将实车模板复制为独立配置文件 `service.properties`（后续升级版本不会覆盖该文件）：

```bash
ROOT="$HOME/thingshello"
REL="$ROOT/releases/car-pi-0.1.0"

mkdir -p "$ROOT/config"
cp "$REL/config/pi3-lgpio.properties" "$ROOT/config/service.properties"
```

> 若小车仅安装排线摄像头（无 USB 摄像头），可将 `$ROOT/config/service.properties` 中的 `camera=v4l2` 改为 `camera=disabled`，避免 USB 设备节点不存在报错；排线摄像头由独立的 `car-pi-rtsp` 服务管理。

### 步骤 2：安装舵机硬件 PWM0 预备服务

`car-pi` 以普通用户运行，因此通过 `car-pi-servo-pwm.service` 在主服务启动前以 `root` 导出 `/sys/class/pwm/pwmchip0/pwm0` 并将权限授予 `gpio` 用户组：

```bash
sudo install -m 755 "$REL/deploy/prepare-servo-pwm.sh" /usr/local/sbin/car-pi-prepare-servo
sudo cp "$REL/deploy/car-pi-servo-pwm.service" /etc/systemd/system/
sudo mkdir -p /etc/systemd/system/car-pi.service.d
sudo cp "$REL/deploy/servo-pwm.conf" /etc/systemd/system/car-pi.service.d/
```

### 步骤 3：生成并启用 `car-pi.service`

将 `car-pi.service.in` 中的 `@USER@` 和 `@ROOT@` 替换为当前用户名与部署根目录绝对路径：

```bash
sed -e "s|@USER@|$USER|g" -e "s|@ROOT@|$ROOT|g" \
  "$REL/deploy/car-pi.service.in" \
  | sudo tee /etc/systemd/system/car-pi.service >/dev/null

sudo systemctl daemon-reload
sudo systemctl enable car-pi.service
```

### 步骤 4：激活首个版本并启动

```bash
"$REL/scripts/activate.sh" "$ROOT" car-pi-0.1.0
```

> **附：安装排线摄像头 H.264 RTSP 服务（`car-pi-rtsp`）**  
> 若使用 CSI 排线摄像头（`rtsp://<小车IP>:8554/csi`），请继续按 [deploy/csi/README.md](csi/README.md) 解压 MediaMTX 到 `/opt/car-pi-rtsp` 并启用 `car-pi-rtsp.service`。

---

## 7. 日常运维与排障命令速查

### 7.1 服务启停与开机自启管理（树莓派上执行）

```bash
# 查看主控制服务与舵机预备服务状态
sudo systemctl status car-pi car-pi-servo-pwm

# 启动 / 停止 / 重启主控制服务
sudo systemctl start car-pi
sudo systemctl stop car-pi
sudo systemctl restart car-pi

# 开启 / 取消开机自启
sudo systemctl enable car-pi
sudo systemctl disable car-pi

# 排线摄像头 RTSP 服务（若已安装）
sudo systemctl status car-pi-rtsp
sudo systemctl restart car-pi-rtsp
```

### 7.2 查看运行日志（树莓派上执行）

```bash
# 实时滚动查看 car-pi 服务日志
journalctl -u car-pi -f

# 查看本次开机以来的 car-pi 日志
journalctl -u car-pi -b --no-pager

# 查看舵机硬件 PWM 初始化脚本日志
journalctl -u car-pi-servo-pwm -b --no-pager

# 实时查看排线摄像头 RTSP 服务日志
journalctl -u car-pi-rtsp -f
```

### 7.3 硬件与网络连通性核验

```bash
# 【树莓派】检查监听端口（TCP 8010 为控制端口，TCP 8554 为 CSI RTSP 端口）
ss -tulnp | grep -E '8010|8000|8554'

# 【树莓派】检查 BCM18 引脚是否已切为硬件 PWM0（应包含 GPIO18 = PWM0）
pinctrl get 18

# 【树莓派】手动运行舵机 PWM 导出脚本测试权限与节点
sudo /usr/local/sbin/car-pi-prepare-servo
ls -l /sys/class/pwm/pwmchip0/pwm0/

# 【树莓派】检查 USB 摄像头设备节点与支持格式
ls -l /dev/v4l/by-id/
v4l2-ctl -d /dev/video0 --list-formats-ext

# 【开发机 Mac】远程探测小车控制端口与 RTSP 视频端口是否开放
nc -vz <小车IP> 8010
nc -vz <小车IP> 8554
```
