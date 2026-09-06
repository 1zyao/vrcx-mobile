package io.github.vrcmteam.vrcm.presentation.vrcx

/**
 * 各平台为 VRCX Mobile 提供只读数据库驱动与连接配置来源。
 * Desktop 用 JDBC 直连远程 PostgreSQL；Android/iOS 的驱动在各自接入前先抛未实现。
 */
expect fun createVrcxDatabaseClient(config: RemoteDatabaseConfig): ReadOnlyDatabaseClient

/** 读取本地持久化的连接配置；文件缺失或解析失败返回 null。 */
expect fun loadVrcxConnectionConfig(): RemoteDatabaseConfig?

/** 保存由设置页面录入的连接配置。密码由各平台实现负责安全存储。 */
expect fun saveVrcxConnectionConfig(config: RemoteDatabaseConfig)

/** 面向用户展示“连接配置应放在哪里”的说明文本。 */
expect fun vrcxConnectionConfigLocation(): String
