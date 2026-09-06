package io.github.vrcmteam.vrcm.presentation.vrcx

import io.github.vrcmteam.vrcm.di.modules.desktopSettingsDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path
import java.util.UUID

/** 桌面连接配置文件名，存放在 VRCM 设置目录下。 */
internal val vrcxConnectionFile: Path
    get() = desktopSettingsDirectory() / "vrcx-connection.json"

private val vrcxJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

actual fun createVrcxDatabaseClient(config: RemoteDatabaseConfig): ReadOnlyDatabaseClient =
    when (config.databaseType) {
        RemoteDatabaseType.PostgreSQL -> JdbcPostgresClient(config)
        RemoteDatabaseType.MySQL -> JdbcMySqlClient(config)
    }

actual fun loadVrcxConnectionConfig(): RemoteDatabaseConfig? {
    val file = vrcxConnectionFile
    val fileSystem = FileSystem.SYSTEM
    if (fileSystem.metadataOrNull(file)?.isRegularFile != true) return null
    return try {
        fileSystem.read(file) {
            vrcxJson.decodeFromString(RemoteDatabaseConfig.serializer(), readUtf8())
        }
    } catch (_: Exception) {
        // 配置损坏时按“未配置”处理，UI 提示按示例重建该文件。
        null
    }
}

actual fun saveVrcxConnectionConfig(config: RemoteDatabaseConfig) {
    val fileSystem = FileSystem.SYSTEM
    val file = vrcxConnectionFile
    val parent = requireNotNull(file.parent)
    fileSystem.createDirectories(parent)
    val temporary = parent / "${file.name}.${UUID.randomUUID()}.tmp"
    try {
        fileSystem.write(temporary, mustCreate = true) {
            writeUtf8(vrcxJson.encodeToString(RemoteDatabaseConfig.serializer(), config))
        }
        fileSystem.atomicMove(temporary, file)
    } finally {
        fileSystem.delete(temporary, mustExist = false)
    }
}

actual fun vrcxConnectionConfigLocation(): String = vrcxConnectionFile.toString()
