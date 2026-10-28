# Maintenance rules
- 优先复用现有代码，不随意重构无关模块，保留原有功能。
- 保持 Kotlin 编码风格、Coroutines/Flow 和 Jetpack Compose Material 3 界面一致性；同步中英文资源。
- 新功能采用模块化设计，优先独立包、接口、状态模型，按需拆分 Gradle 模块。
- 修改后执行相关测试；工程改动执行 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug。
- 小步提交，尽量每次提交可编译，开发分支通过 PR 合并，禁止直接覆盖 main。
- 不提交密钥、密码、签名文件、设备身份或 local.properties。
- 不修改原作者版权信息，保留 LICENSE，遵守 GPL-3.0，分发时提供对应源码及变更说明。
- versionCode 单调递增；发布必须使用固定签名；上游同步不得自动合并，冲突等待审核。
- 先阅读 docs/README.md 和 docs/ENHANCED.md。
