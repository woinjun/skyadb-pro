# SkyADB Enhanced 第一阶段维护与分析

基线：sky22333/skyadb，提交 e1cc020543a51c0223d7f2ffe96b9da799d80f90。
本次仅修改工程、版本与自动化，未更改核心业务逻辑。

## 架构
单模块 :app（settings.gradle.kts 明确 include）。app/src/main/java/com/sky22333/skyadb
采用 MainActivity → AdbManagerApp（NavHost/Scaffold）→ 各 Screen / ViewModel →
AdbRepository / ScrcpyRepository → KadbManager / FastbootOtgManager。
AppServices 是手工服务容器；ViewModel 通过 StateFlow 和协程维护页面状态。
ui/components 和 ui/theme 共享 Material 3 样式；res/values 与 values-zh-rCN 提供双语。
data 使用 DataStore 保存设置与最近设备；model 表示状态/结果；
validation 校验网络地址、下载 URL、设备路径和文件名。

adb/KadbManager 封装 Kadb TCP 连接、无线配对、USB ADB、Shell 和文件流，
使用 IO 调度、Mutex、超时与关闭连接处理取消；AdbIdentityManager 管理身份。
usb 包负责 Android USB Host 权限、接口与插拔；Fastboot 使用独立协议通道。
discovery 提供 NSD/mDNS、网段扫描和 ADB 协议探测。
scrcpy 推送内置 v5.0.1 server，经 Kadb localabstract 流传输，
MediaCodec/SurfaceView 解码画面，独立音频解码与控制通道。
镜像触控经过坐标转换，按键以 scrcpy 控制消息发送。
RemoteControlViewModel 的 17 个遥控键通过 input keyevent 执行。
镜像文本通过 setClipboard(paste=true) 注入，已有中文输入路径，
但没有完整的双向远程剪贴板产品功能。

## 已有与待开发
| 路线 | 源码现状与下一步 |
| --- | --- |
| ADB 无线鼠标、手机触控板 | 已有镜像绝对触控；独立相对鼠标、滚轮、手势映射需新增，复用控制通道 |
| ADB 飞鼠 | 未实现；新增 SensorManager 采样、姿态融合、校准、滤波与节流 |
| ADB 虚拟键盘 | 已有镜像文本和按键；独立完整键盘、组合键与布局需扩展 |
| 蓝牙 HID 鼠标/飞鼠/键盘 | 未找到 BluetoothHidDevice 或蓝牙权限/服务；需独立 transport，评估 API 28+ 与厂商支持 |
| 自定义遥控器 | 已有固定 17 键遥控器；可配置布局/动作需新增，复用 RemoteKey 与 Shell |
| 自动化快捷指令 | 已有手动 Shell；模板、参数、序列执行、取消及持久化需新增 |
| 多设备管理 | 已有最近 WiFi 设备；KadbManager 是单活动连接，配置档案与并发连接需分别设计 |
| 远程剪贴板 | 已有向远端写剪贴板并粘贴；读取/同步/权限及隐私 UI 需新增 |
| 性能监控 | 已有设备基础信息、电池、日志；CPU/内存采样、曲线与开销控制需新增 |
| CI / Release / 同步 | 原有检查与手动发布，本阶段扩展；尚需远端验证 |
| 应用内更新 | 未发现完整更新实现；后续应校验来源、包名、证书及版本，交由系统安装 |

其它已有功能：应用搜索/启动/停止/卸载、APK 安装、在线下载后安装/推送、
本机应用导出、文件双栏管理、截图、系统日志与诊断日志、主题/超时/画质设置。
无需重复开发上述功能。设备相关能力目前仅通过源码确认，未做真机验收。

## 工程与测试
Gradle 9.8.1（Wrapper 含 SHA-256）、AGP 9.4.1、Kotlin 2.4.21、
Compose BOM 2026.09.00；compile/target 37、min 24；JDK 17。
依赖由 gradle/libs.versions.toml 管理。保持上游版本，不为通过构建盲目降级。
17 个 JVM 测试源码文件，覆盖校验器、协议、发现、遥控键、日志及应用显示等；
7 个 androidTest 文件，覆盖 USB/Fastboot、镜像/解码与遥控 UI。
普通 CI 执行 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug。
设备测试需连接真机或适合的模拟器，USB/目标设备相关 smoke test 不能仅凭云构建宣称通过。

## 工作流
- android-checks.yml：PR、main/develop push、手动触发；JDK/SDK 配置、Gradle 缓存、
  JVM 测试、Lint、Debug APK。Artifact 为 skyadb-debug-提交SHA；失败日志与报告始终上传。
- android-ci.yml：v* Tag 触发，严格核对 vMAJOR.MINOR.PATCH、version.properties、
  versionCode、独立包名；release 环境中解码固定 PKCS12 签名，测试、Lint、构建、
  apksigner 验证、SHA256SUMS、生成 Release 更新记录。结束删除临时签名文件。
  不覆盖已有 Release，缺少密钥直接失败。
- upstream-sync.yml：每天 UTC 02:23（北京时间 10:23）及手动检查上游默认分支。
  发现更新创建提醒 Issue，合并成功时只更新 automation/upstream-sync 并创建 PR；
  冲突时中止合并并列出冲突文件。绝不自动合并到默认分支。
  自动化分支专供机器人，请勿直接提交个人修改。

## 版本、包名与签名
version.properties 集中管理 VERSION_NAME 与 VERSION_CODE。
初始增强版本 1.2.0 / 101209。上游源码默认 code=10000，但 v1.1.6 发布公式为
(1*10000+1*100+6)*10+9=101069，因此不能仅沿用默认值。
建议未来继续使用 (major*10000+minor*100+patch)*10+9（minor/patch 小于100），
且每次发布显式检查 code 高于所有历史已发布 APK；不要回退或重用 code。
如历史分发过更高 code，必须先提高本项目 code。

Debug 包名 com.sky22333.skyadb.enhanced.debug，与原版并存；临时 Debug 签名
在不同 Actions runner 间不稳定，仅作测试，跨次安装可能需卸载测试包。
正式包名由 release 环境变量 ENHANCED_APPLICATION_ID 指定，
建议 io.github.<你的GitHub用户名>.skyadb.enhanced；发布流程拒绝原版包名。
namespace 不必跟随 applicationId 修改。包名一经正式发布应固定。
同包名但不同证书不能覆盖原版，不能通过提高 versionCode 绕过；
卸载会丢失原版私有数据。独立包名可与原版并存。

## GitHub 首次启用（需要仓库所有者）
1. 在 GitHub 原仓库页面点击 Fork，选择自己的账号与仓库名，保持上游历史。
   已确认目标 woinjun/skyadb-pro 是 sky22333/skyadb 的 Fork，默认分支 main；本地配置 origin 为该 Fork、upstream 为原仓库。
   GitHub 仓库地址：https://github.com/woinjun/skyadb-pro。远端推送和 PR 尚待明确确认。
2. 确认授权推送本地 develop/phase1-foundation 并向 main 创建 PR。
   不要将增强分支推送到原作者仓库。
3. Fork 中 Settings → Actions → General 启用 Actions。
   如需机器人创建同步 PR，启用 Allow GitHub Actions to create and approve pull requests。
   工作流只创建 PR，不审批或合并。受组织限制时从提醒 Issue 手动创建 PR。
   GITHUB_TOKEN 创建的同步 PR 可能不会触发普通 PR CI；合并前需对该分支手动运行 checks。
4. PR 合并到 main 后手动运行 Android checks，或向 develop 提交触发。
   在 Actions → Android checks → 成功运行 → Artifacts 下载 skyadb-debug-* ZIP，
   解压安装 APK。未运行前不存在可用下载链接。

## 正式发布（不需要在聊天中提供任何密码）
1. 首先确认正式 applicationId，创建 Settings → Environments → release，
   配置 required reviewers（账号方案支持时），限制为受控发布 Tag。
2. 在可信离线机器生成并长期备份 PKCS12 密钥：
   keytool -genkeypair -storetype PKCS12 -keystore skyadb-enhanced.p12 -alias skyadb-enhanced -keyalg RSA -keysize 3072 -validity 10000
   密码通过交互输入。固定密钥不可丢失，不提交仓库。
3. Windows PowerShell 将密钥转 Base64：
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path ./skyadb-enhanced.p12))) | Set-Clipboard
   粘贴到 release 环境 Secret SIGNING_KEY_BASE64，随后清空剪贴板。
   另添加 KEY_ALIAS、KEY_STORE_PASSWORD、KEY_PASSWORD。
   环境 Variable ENHANCED_APPLICATION_ID 填已确定的独立包名。
4. 更新 version.properties，经 PR 测试合并后，在确认发布的提交创建并推送对应 Tag，
   如 v1.2.0（推 Tag 会触发正式发布，需明确授权）。
5. 审批 release 环境后检查日志、证书指纹、APK 与 SHA256SUMS，
   在 GitHub Releases 下载。发布前真机验证安装、重启后连接、镜像、
   键盘与从上一正式版本覆盖升级。
6. 保留 GPL LICENSE、原作者版权及依赖许可，Release 对应 Tag 提供完整对应源码；
   内置 scrcpy server 需维持其来源与许可信息，正式发布前核对第三方分发材料。

## 上游手工维护
git remote add upstream https://github.com/sky22333/skyadb.git
（本地已添加，不必重复）
git fetch upstream
从自己的 main 创建独立同步分支，merge upstream 的默认分支，解决冲突、
执行检查后创建 PR。禁止 reset --hard 覆盖增强代码。机器人权限不足时检查
Actions 错误与 Issue，改用上述手工流程，不授予长期高权限 PAT。

## 下一阶段
先完成 Fork 远端 CI 与一台目标设备验收，再做独立 ADB 触控板：
抽象输入 transport、复用 scrcpy 控制、实现移动/点击/滚轮与节流。
随后扩展键盘，再加入姿态传感器与 Bluetooth HID，避免同时修改连接层和 UI。


## 2026-10-28 本地验收
- JDK Temurin 17.0.20.1、Gradle 9.8.1、SDK API 37 已在工作目录配置。
- 实际命令：gradlew.bat --no-daemon --stacktrace --console=plain :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
- BUILD SUCCESSFUL in 4m 45s，53 项任务执行。
- 64 项 JVM 测试通过，0 failures / errors / skipped；Lint 0 errors、64 warnings。
  警告保留在报告中，本阶段未为清零警告修改业务逻辑。
- actionlint 1.7.12 检查全部三个工作流通过；git diff --check 通过。
- APK 实际存在，约 25.70 MiB；apksigner verify 通过，Android Debug v2 签名。
- aapt 确认包名 com.sky22333.skyadb.enhanced.debug、versionCode 101209、
  versionName 1.2.0-debug、minSdk 24、targetSdk 37。
- APK SHA-256：ce2805b5efc700ffecc67a1dc4a3b5ab049575893391e3421a9f310a8ffbfde1
- 未执行真机/仪器测试、正式签名 Release 构建、远端 Actions 或正式发布。
  远端流程配置完成，仍需推送 PR、在 GitHub 实际运行后才能验收远端自动构建。
- 构建中的 Android analytics 目录不可写提示不影响结果；Gradle 10 弃用提示
  留待将来升级处理。未修改 Wrapper 或依赖版本。

## 本次文件清单
新增 AGENTS.md、version.properties、docs/ENHANCED.md、
.github/workflows/upstream-sync.yml。
修改 .gitignore、app/build.gradle.kts、README.md、docs/README.md、
.github/workflows/android-checks.yml、.github/workflows/android-ci.yml。
原作者版权与 LICENSE 原样保留。

## APK 获取
本地交付文件 SkyADB-Enhanced-1.2.0-debug.apk。
远端待授权部署后，打开 https://github.com/woinjun/skyadb-pro/actions，
选择 Android checks 的成功运行，在 Artifacts 下载 skyadb-debug-*。
正式版在 https://github.com/woinjun/skyadb-pro/releases 获取；
目前未创建任何正式 Release。

## 远端部署状态（2026-10-28）
用户已明确授权推送开发分支并创建 PR，但执行被认证权限阻止：
- 命令行 Git 无可用的非交互登录凭据；默认凭据助手无法启动。
- GitHub 连接器读取 main 成功，确认其仍为上游基线 e1cc020；
  创建 tree 对象时返回 HTTP 403 Resource not accessible by integration。
- 没有成功创建远端分支、PR、Actions 运行或 Release。
- 请在 https://github.com/settings/installations 找到当前连接使用的应用，
  Configure → Repository access，将 woinjun/skyadb-pro 纳入访问范围；
  检查是否有待接受的 Contents / Workflows / Pull requests 写入权限更新。
  若当前 GitHub 连接只提供读取权限，需要重新连接具有写入权限的账号/应用，
  或完成本机 GitHub 登录。不需要把密码或 Token 发到聊天中。
授权修复后继续现有开发分支推送，不重新修改 main，不需要再次确认任务授权。
