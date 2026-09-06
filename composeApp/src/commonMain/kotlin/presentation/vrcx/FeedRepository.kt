package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.serialization.Serializable

/** 远程数据库返回的行必须保持 SELECT 列顺序，避免暴露具体 JDBC/Native 驱动。 */
interface ReadOnlyDatabaseClient {
    suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>>
}

enum class FeedSqlDialect {
    PostgreSQL,
    MySQL,
}

@Serializable
enum class RemoteDatabaseType {
    PostgreSQL,
    MySQL,
}

@Serializable
data class RemoteDatabaseConfig(
    val host: String,
    val port: Int = 5432,
    val database: String,
    val username: String,
    val password: String,
    val accountPrefix: String,
    val tls: Boolean = true,
    val databaseType: RemoteDatabaseType = RemoteDatabaseType.PostgreSQL,
)

class FeedRepository(
    private val database: ReadOnlyDatabaseClient,
    private val config: RemoteDatabaseConfig,
) {
    suspend fun load(filter: FeedFilter): List<VrcxFeedEvent> {
        val args = buildMap {
            filter.userId?.let { put("userId", it) }
            if (filter.query.isNotBlank()) put("query", "%${filter.query}%")
            if (!filter.worldName.isNullOrBlank()) put("worldName", "%${filter.worldName}%")
            filter.fromCreatedAt?.takeIf { it.isNotBlank() }?.let { put("fromCreatedAt", it) }
            filter.toCreatedAt?.takeIf { it.isNotBlank() }?.let { put("toCreatedAt", it) }
            filter.cursor?.let {
                put("cursorCreatedAt", it.createdAt)
                put("cursorId", it.rowId)
            }
        }
        return database.query(
            FeedQuery.build(
                database = config.database,
                accountPrefix = config.accountPrefix,
                filter = filter,
                dialect = config.databaseType.toFeedDialect(),
            ),
            args,
        ).map(::mapRow)
    }

    private fun mapRow(row: List<Any?>): VrcxFeedEvent {
        require(row.size >= 22) { "Feed 查询返回列数不足" }
        fun text(index: Int) = row[index]?.toString()
        fun long(index: Int) = when (val value = row[index]) {
            is Number -> value.toLong()
            null -> null
            else -> value.toString().toLongOrNull()
        }
        return VrcxFeedEvent(
            rowId = long(0) ?: error("Feed id 为空"),
            createdAt = text(1).orEmpty(),
            userId = text(2).orEmpty(),
            displayName = text(3).orEmpty(),
            type = FeedType.entries.firstOrNull { it.name == text(4) }
                ?: error("未知 Feed 类型: ${text(4)}"),
            location = text(5), worldName = text(6), previousLocation = text(7), time = long(8),
            groupName = text(9), status = text(10), statusDescription = text(11),
            previousStatus = text(12), previousStatusDescription = text(13), bio = text(14),
            previousBio = text(15), ownerId = text(16), avatarName = text(17),
            currentAvatarImageUrl = text(18), currentAvatarThumbnailImageUrl = text(19),
            previousCurrentAvatarImageUrl = text(20),
            previousCurrentAvatarThumbnailImageUrl = text(21),
        )
    }

    private fun RemoteDatabaseType.toFeedDialect() = when (this) {
        RemoteDatabaseType.PostgreSQL -> FeedSqlDialect.PostgreSQL
        RemoteDatabaseType.MySQL -> FeedSqlDialect.MySQL
    }
}
