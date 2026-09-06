# VRCX Mobile

VRCX Mobile 是一个使用 Kotlin Multiplatform 和 Compose Multiplatform 编写的 VRCX 远程日志只读浏览器。

它不负责采集 VRChat 日志。电脑上的 VRCX-K 继续负责采集和写入，手机或桌面客户端通过只读数据库账号查看 Feed 记录。

```text
VRCX-K Collector -> PostgreSQL / MySQL / MariaDB <- VRCX Mobile Viewer
```

## 当前功能

- 浏览 VRCX Feed 时间线
- 查看位置、状态、简介、模型、上线和下线记录
- 显示世界名称和简短房间标识
- 使用 VRCX 风格的包含式模糊搜索
- 按 Feed 类型筛选
- 使用游标分页加载历史记录
- 在应用内填写、测试和保存数据库连接
- 自动发现数据库中的 VRCX 账号
- 数据库连接使用只读事务
- Android、Desktop 使用 JDBC
- iOS 使用 sqlx4k Native

## 不包含的功能

- 不采集手机本地 VRChat 日志
- 不实现 VRChat 登录
- 不提供任意 SQL 查询
- 不修改数据库内容
- 不包含 Gateway 或中转服务器
- 不使用远程 SQLite
- 不提供后台常驻采集

## 数据库要求

VRCX Mobile 连接由 VRCX-K 创建的远程数据库。当前支持：

- PostgreSQL
- MySQL
- MariaDB

建议创建专用数据库只读账号。应用层会设置只读事务，但数据库账号本身也必须只有 `SELECT` 权限。

连接信息在应用的连接设置页面中填写，不需要导入外部 JSON 文件：

- 数据库类型
- 地址和端口
- 数据库名称
- 用户名和密码
- TLS 开关

保存时应用会从数据库元数据中发现可用的 VRCX 账号并要求选择。账号前缀不会写进 SQL 标识符，应用只接受符合 VRCX 命名规则的值。

不要把数据库密码、连接配置或生产数据库地址提交到仓库、日志或 Issue。

## 平台状态

| 平台 | 状态 | 数据库实现 |
| --- | --- | --- |
| Android | 可构建，可测试 | PostgreSQL JDBC、MariaDB Connector/J |
| Windows / Desktop | 可构建，可测试 | PostgreSQL JDBC、MariaDB Connector/J |
| iOS | 可构建 IPA | sqlx4k Native |

iOS 构建需要 macOS/Xcode。GitHub Actions 使用 macOS Runner 构建设备 IPA；当前 IPA 不包含正式 App Store 签名，越狱设备或用户自己的签名流程可用于安装测试。

当前限制：iOS 数据库连接尚未完成真实设备端到端验证；iOS TLS 和数据库认证能力以 sqlx4k Native 实际支持为准。

## 构建

环境要求：

- JDK 21
- Gradle Wrapper
- Android SDK 36（构建 Android 时）
- macOS 和 Xcode（构建 iOS 时）

构建 Desktop：

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:run
```

启动 Desktop 预览数据：

```bash
VRCX_PREVIEW=1 ./gradlew :composeApp:run
```

构建 Android Debug APK：

```bash
./gradlew :composeApp:assembleDebug
```

构建 iOS Framework：

```bash
./gradlew :composeApp:linkDebugFrameworkIosArm64
```

设备 IPA 由 GitHub Actions 在 macOS Runner 上构建。构建工作流位于 `.github/workflows/ios-build.yml`。

## 项目结构

```text
composeApp/
  src/commonMain/   Feed 模型、查询、Repository、Compose UI
  src/androidMain/  Android JDBC 和平台实现
  src/desktopMain/  Desktop JDBC 和平台实现
  src/iosMain/      iOS Native 平台实现
iosApp/             iOS Xcode 应用壳
```

核心数据流：

```text
连接设置
  -> 只读数据库客户端
  -> FeedQuery 固定参数化查询
  -> FeedRepository
  -> Compose 页面
```

数据库表结构沿用 VRCX-K 的 Feed 表：`feed_gps`、`feed_status`、`feed_bio`、`feed_avatar` 和 `feed_online_offline`。

## 安全说明

- 使用数据库专用只读账号
- 不开放任意 SQL
- 用户输入通过参数绑定
- 数据库表名和账号前缀经过白名单校验
- 生产数据库建议通过 VPN 或私有网络访问
- 不要直接把数据库端口暴露到公网
- 不要在日志、截图或 Issue 中公开密码

## 许可证

本项目使用 MIT License，详见 [LICENSE](LICENSE)。

VRCX Mobile 与 VRChat、VRCX-K 无隶属关系。使用时请遵守 VRChat 的服务条款和适用法律。

## 反馈

请在 [GitHub Issues](https://github.com/1zyao/vrcx-mobile/issues) 提交问题，并附上复现步骤、平台和相关日志。提交日志前请删除数据库地址、用户名、密码和其他敏感信息。
