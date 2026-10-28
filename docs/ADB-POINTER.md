# ADB 触控板与陀螺仪飞鼠（1.3.0 测试版）

本迭代新增独立的 ADB 触控板、ADB 陀螺仪飞鼠，修正设置中的项目地址。
蓝牙 HID 飞鼠未纳入此版，按用户选定的顺序后续实现。

## 使用入口
1. 首页通过 WiFi ADB、无线调试配对或 USB ADB 连接目标设备。
2. 打开底部「设备详情」→「ADB 触控板 / 飞鼠」。
3. 点击「启动控制」，检查目标设备出现系统鼠标指针。
4. 单指滑动移动，轻触点击，双指上下滑动滚轮；也有独立左右键。
   「按住左键 / 拖动」是保持开关，再次点击释放；点击左右键也会结束拖动。
5. 开启飞鼠后先竖握并静止约 0.8 秒，之后左右转动、上下俯仰控制指针。
   可调整灵敏度；漂移或换握姿时暂停后重新校准。
6. 退出页面、切后台、停止控制会关闭专用控制通道并释放虚拟鼠标。

## 支持范围与限制
- 使用原有 Kadb 连接与 scrcpy-server 5.0.1；无需 root，无需开启镜像画面。
- 目标需要可由 ADB shell 读写 /dev/uhid；旧系统或厂商 SELinux 策略可能不允许。
  不支持时显示错误，不会伪装成功。通道就绪表示协议写入成功，尚不代表经过真机验收。
- 飞鼠需要控制手机具备陀螺仪；无陀螺仪时只禁用飞鼠，触控板仍可使用。
- 物理 HID 相对指针由目标系统处理，不依赖固定屏幕分辨率。
- 蓝牙 HID、独立完整键盘、自定义遥控器不在本版范围。
- 本版仍是 Debug 测试 APK。不同构建机的 Debug 证书可能不同；
  如提示签名冲突，先卸载增强版测试包（会清除其本地设置），不必卸载原版。
  正式连续升级仍需固定正式签名。

## 实现
pointer/MouseProtocol.kt：标准三键、X/Y、滚轮 HID 描述符，scrcpy UHID
create/input/destroy 帧，以及有界移动累积、保持完整的按键转换队列。
16ms 批量发送，保留小数移动；超大累计输入设上限避免断线后的积压跳跃。
pointer/AdbMouseSession.kt：独立 scid 的 control-only 会话，关闭视频、音频和剪贴板自动同步，
复用内置 server 与 Kadb 多路复用，不为每个移动启动 Shell。
ui/pointer/PointerViewModel.kt：连接、发送、故障状态和资源释放；
ui/pointer/PointerScreen.kt：触控手势、鼠标按钮、飞鼠开关及生命周期管理。
GyroMotion：角速度积分，启动静止偏置估计、死区、低通滤波、屏幕旋转映射；
间隔过大的采样不积分，避免恢复后跳跃。滤波和灵敏度仍需真机调优。
连接中断、服务器错误、离开页面或后台均停止输入，不修改主 ADB 连接实现。

设置中增强版地址改为 https://github.com/woinjun/skyadb-pro，
另保留 https://github.com/sky22333/skyadb 来源入口。LICENSE 和版权未改动。
版本递增为 1.3.0 / 101309。

## 测试范围
新增 JVM 测试覆盖 UHID 帧边界、有符号移动/滚轮、溢出拒绝、
移动分包及小数累计、点击释放/拖动状态、静止偏置校准、暂停采样和横屏轴映射。
完整工程验证命令：
gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

## 必须完成的真机验收（尚未执行）
- 一台控制手机 + 一台目标 Android/电视，WiFi ADB 和 USB ADB 分别测试。
- 验证移动、左/右键、滚轮、保持拖动和松开，目标系统实际出现指针。
- 验证飞鼠校准、灵敏度、横竖屏、暂停/恢复和静止漂移。
- 拖动时退出、切后台、锁屏、断网，确认目标不残留按住状态。
- 连接失败时错误可见；停止后重新连接；快速连续开始/停止不产生旧会话。
- 没有陀螺仪的控制设备，以及拒绝 UHID 的目标设备，错误/禁用状态符合说明。
- 回归原有配对、Shell、镜像、键盘、文件、应用管理与遥控器。
- 设置页增强版与原项目两个链接各自跳转正确。

## 依据
- scrcpy 5.0.1 Mouse 文档：https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/mouse.md
- scrcpy ControlMessageReader、DesktopConnection、UhidManager 对应版本源码。
- Android Motion sensors：https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion

## 本地验证结果（2026-10-28）
最终执行 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug，BUILD SUCCESSFUL。
73 项 JVM 测试通过（含本次新增 9 项），0 failures/errors/skipped。
Lint 为 0 errors、64 warnings；没有禁用 Lint 或增加 baseline 来隐藏问题。
APK 实际存在，apksigner v2 签名校验通过。
aapt 确认 1.3.0-debug / versionCode 101309，包名 com.sky22333.skyadb.enhanced.debug。
APK SHA-256：6d9e182d6f63882a622e80e7a43bcd4230d6ad5fdb4765ef9d88fcbcfb32cea4
本机沙箱曾导致 Kotlin 临时目录失败，最终将 TEMP/TMP 与 java.io.tmpdir
定向工作区后正常构建；未为本机问题修改项目 Gradle 依赖或 CI。
没有 Android 真机连接，本次未执行仪器测试或宣称真机功能通过。
目前仅完成本地开发分支 develop/adb-pointer，尚未上传此功能版本或创建新 PR。
第一阶段 PR #1 已通过云端构建，仍保持未合并。
