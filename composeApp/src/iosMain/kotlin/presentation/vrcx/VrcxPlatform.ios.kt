package io.github.vrcmteam.vrcm.presentation.vrcx

import io.github.vrcmteam.vrcm.storage.IosKeychainSecureStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CONNECTION_KEY = "connection"
private val vrcxJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val vrcxKeychain = IosKeychainSecureStorage("io.github.vrcmteam.vrcm.vrcx")

actual fun createVrcxDatabaseClient(config: RemoteDatabaseConfig): ReadOnlyDatabaseClient =
    throw NotImplementedError("VRCX Mobile iOS 端数据库驱动尚未接入")

actual fun loadVrcxConnectionConfig(): RemoteDatabaseConfig? = runCatching {
    vrcxKeychain.get(CONNECTION_KEY)?.let {
        vrcxJson.decodeFromString(RemoteDatabaseConfig.serializer(), it)
    }
}.getOrNull()

actual fun saveVrcxConnectionConfig(config: RemoteDatabaseConfig) {
    vrcxKeychain.put(
        CONNECTION_KEY,
        vrcxJson.encodeToString(RemoteDatabaseConfig.serializer(), config),
    )
}

actual fun vrcxConnectionConfigLocation(): String = "iOS Keychain（由应用内设置管理）"
