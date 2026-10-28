# ADB 触控板与陀螺仪飞鼠

## 使用
1. 通过 WiFi ADB、无线调试配对或 USB ADB 连接目标设备。
2. 打开「设备详情」→「ADB 触控板 / 飞鼠」。进入页面自动准备控制，无需点击启动。
3. 单指滑动移动，轻触点击，双指上下滑动滚轮；独立左右键和保持拖拽按钮也可使用。
4. 下方飞鼠默认关闭。开启后竖握并静止约 0.8 秒，再左右转动、上下俯仰控制指针。
   换握姿前关闭飞鼠；漂移时保持静止重新校准。
5. 离开页面、切后台会关闭专用通道并释放虚拟鼠标；再次进入自动准备触控板，飞鼠不会自动开启。
6. 失败原因显示在页面上，可重试；未连接时先返回连接设备。

## 支持范围
- 复用 Kadb 和 scrcpy-server 5.0.1，无需 root，无需开启镜像画面。
- 目标须允许 ADB shell 读写 `/dev/uhid`；旧系统或厂商安全策略可能不支持。
- 飞鼠需要控制手机具备陀螺仪；无传感器时仍可使用触控板。
- 相对指针由目标系统处理，不依赖固定分辨率。
- 蓝牙控制独立于 ADB，见 [蓝牙使用说明](BLUETOOTH-POINTER.md)。

## 实现与验证
`MouseProtocol` 定义三键、X/Y、滚轮 HID 描述符以及 scrcpy UHID 帧。
`MouseMotionBuffer` 有界合并运动，保留小数及完整按键转换，16ms 批量发送。
`AdbMouseSession` 使用独立 scid 的 control-only 会话，不修改主 ADB 连接。
`PointerController` 复用触控、拖拽和陀螺仪处理，ADB 与蓝牙分别管理连接生命周期。
`PointerScreen` 提供共用控件，页面恢复时启动、停止时释放。
`GyroMotion` 处理静止偏置、死区、低通滤波、屏幕旋转映射与采样间隔保护。

JVM 测试覆盖报告、队列、按键释放、校准、旋转和断开时输入门控。
工程检查执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
真机应测试 WiFi/USB、点击/滚动/拖拽、飞鼠静止漂移、快速进出、锁屏、断线和无 UHID 设备。
同时回归配对、Shell、镜像、键盘、文件及应用管理。

## 依据
- [scrcpy Mouse 文档](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/mouse.md)
- [Android Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
