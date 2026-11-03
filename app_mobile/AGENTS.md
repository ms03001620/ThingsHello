# `app_mobile` 模块开发指南与本地约定

本文件定义 `app_mobile`（手机端遥控与 RTSP 视频预览应用）的架构分层、目录索引与本地编码约定。在修改 `app_mobile` 模块前，必须先阅读并遵守本文档。

---

## 1. 源码结构与分层（Kotlin + Java 混编）

- **Kotlin 源码根目录**：`app_mobile/src/main/kotlin/org/mark/mobile/`
- **Java 源码根目录**：`app_mobile/src/main/java/org/mark/mobile/`
- **单元测试目录**：`app_mobile/src/test/java/org/mark/mobile/`

| 包 / 目录 | 职责说明 | 核心文件 |
| --- | --- | --- |
| `org.mark.mobile` | 页面入口（Activity / View 层） | `MainActivity.kt`（连接页，MVVM）、`CtrlActivity.java`（遥控页） |
| `viewmodel/` | MVVM 视图模型与 UI 状态定义 | `MainViewModel.kt`、`MainUiState.kt` |
| `repository/` | MVVM 数据仓库层（隔离底层通信与 SP） | `MainRepository.kt`（`IMainRepository`） |
| `connect/` | TCP 连接管理单例 | `ConnectedManager.java` |
| `ctrl/` | 遥控指令节流与差速混控算法 | `SpeedMixer.java`、`Throttle.java` |
| `preview/` | RTSP H.264 全屏预览与低延迟渲染 | `H264Activity.java`、`LowLatencyRenderersFactory.java` |
| `ui/cyber/` | Cyberpunk 2077 风格自定义控件与 Drawable | `CutShapeHelper.java`、`OctagonJoystickView.java`、`CyberSlider.java`、`ScanlineDrawable.java` |
| `utils/` | 手机端本地配置与通用扩展函数 | `PreferUtils.java`、`ViewExtensions.kt` |

---

## 2. ViewModel 本地编码约定（强制执行）

在 `app_mobile` 中编写或修改 `ViewModel` 与 `LiveData` 时，**必须严格遵守以下本地约定**：

### 2.1 简化 `MutableLiveData` 声明（禁止 `_xxx` Backing Property）
- **禁止**使用传统的私有 `_uiState` + 公开 `uiState` 双属性样板写法：
  ```kotlin
  // ❌ 禁止写法
  private val _uiState = MutableLiveData(MainUiState())
  val uiState: LiveData<MainUiState> = _uiState
  ```
- **必须**直接声明为公开的 `val xxx = MutableLiveData(...)`，并在 `ViewModel` 内部直接对其读写：
  ```kotlin
  // ✅ 正确写法
  val uiState = MutableLiveData(MainUiState())
  ```

### 2.2 严禁在 `ViewModel` 外部直接修改 `LiveData` 的值
- 在 `Activity`、`Fragment` 等 `ViewModel` 外部类中，只允许调用 `observe(...)` 订阅状态或读取 `uiState.value`。
- **严禁**在 `ViewModel` 外部直接赋值修改状态：
  ```kotlin
  // ❌ 严禁在 Activity / Fragment 等外部类中直接修改
  viewModel.uiState.value = xxx
  viewModel.uiState.postValue(xxx)
  ```
- 当外部需要触发状态变更时，**必须由 `ViewModel` 提供显式的方法**（例如 `fun setUiState(newState: MainUiState)` 或具体的业务动作方法）在 `ViewModel` 内部完成修改：
  ```kotlin
  // ✅ 在 ViewModel 中提供修改方法
  fun setUiState(newState: MainUiState) {
      uiState.value = newState
  }
  ```

---

## 3. 构建与验证命令

在仓库根目录执行：

```bash
# 编译 Debug APK
./gradlew :app_mobile:assembleDebug

# 运行单元测试（含 MainViewModelTest 等）
./gradlew :app_mobile:testDebugUnitTest
```
