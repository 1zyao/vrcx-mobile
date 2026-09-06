package io.github.vrcmteam.vrcm.presentation.vrcx

import android.content.Context
import io.github.vrcmteam.vrcm.storage.AndroidSecureStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal lateinit var vrcxAndroidApplicationContext: Context

private const val CONNECTION_KEY = "connection"
private val vrcxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun vrcxStorage() = AndroidSecureStorage(vrcxAndroidApplicationContext, "vrcx-mobile")

actual fun createVrcxDatabaseClient(config: RemoteDatabaseConfig): ReadOnlyDatabaseClient =
    when (config.databaseType) {
        RemoteDatabaseType.PostgreSQL -> JdbcPostgresClientAndroid(config)
        RemoteDatabaseType.MySQL -> JdbcMySqlClientAndroid(config)
    }

actual fun loadVrcxConnectionConfig(): RemoteDatabaseConfig? = runCatching {
    vrcxStorage().get(CONNECTION_KEY)?.let {
        vrcxJson.decodeFromString(RemoteDatabaseConfig.serializer(), it)
    }
}.getOrNull()

actual fun saveVrcxConnectionConfig(config: RemoteDatabaseConfig) {
    vrcxStorage().put(
        CONNECTION_KEY,
        vrcxJson.encodeToString(RemoteDatabaseConfig.serializer(), config),
    )
}

actual fun vrcxConnectionConfigLocation(): String = "Android 系统安全存储（由应用内设置管理）"
