package io.github.vrcmteam.vrcm.presentation.vrcx

/** SQL 契约：值由数据库驱动绑定，UI 不直接拼接查询。 */
internal object FeedQuery {
    private const val columns = """
        id, created_at, user_id, display_name, type, location, world_name,
        previous_location, time, group_name, status, status_description,
        previous_status, previous_status_description, bio, previous_bio,
        owner_id, avatar_name, current_avatar_image_url,
        current_avatar_thumbnail_image_url, previous_current_avatar_image_url,
        previous_current_avatar_thumbnail_image_url
    """

    fun build(
        database: String,
        accountPrefix: String,
        filter: FeedFilter,
        dialect: FeedSqlDialect = FeedSqlDialect.PostgreSQL,
    ): String {
        require(database.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "非法数据库名" }
        require(accountPrefix.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "非法账号前缀" }
        val sources = filter.types.map { source(database, accountPrefix, it, filter, dialect) }
        require(sources.isNotEmpty()) { "至少选择一种事件类型" }
        val outerColumns = columns.trimIndent().split(",").mapIndexed { i, name ->
            "c$i AS ${name.trim()}"
        }.joinToString(", ")
        return "SELECT $outerColumns FROM (${sources.joinToString(" UNION ALL ")}) feed " +
            "ORDER BY c1 DESC, c0 DESC LIMIT ${filter.limit.coerceIn(1, 100)}"
    }

    private fun source(
        database: String,
        accountPrefix: String,
        type: FeedType,
        filter: FeedFilter,
        dialect: FeedSqlDialect,
    ): String {
        val table = when (type) {
            FeedType.GPS -> "feed_gps"
            FeedType.Status -> "feed_status"
            FeedType.Bio -> "feed_bio"
            FeedType.Avatar -> "feed_avatar"
            FeedType.Online, FeedType.Offline -> "feed_online_offline"
        }
        val typeSql = if (type == FeedType.Online || type == FeedType.Offline) {
            " AND type = '${type.name}'"
        } else ""
        val cursorSql = filter.cursor?.let {
            " AND (created_at < :cursorCreatedAt OR " +
                "(created_at = :cursorCreatedAt AND id < :cursorId))"
        } ?: ""
        val userSql = if (filter.userId == null) "" else " AND user_id = :userId"
        val searchSql = if (filter.query.isBlank()) "" else {
            val operator = if (dialect == FeedSqlDialect.PostgreSQL) "ILIKE" else "LIKE"
            " AND (display_name $operator :query OR user_id $operator :query)"
        }
        val worldSql = if (filter.worldName.isNullOrBlank()) "" else {
            val operator = if (dialect == FeedSqlDialect.PostgreSQL) "ILIKE" else "LIKE"
            " AND world_name $operator :worldName"
        }
        val fromSql = filter.fromCreatedAt?.takeIf { it.isNotBlank() }?.let {
            " AND created_at >= :fromCreatedAt"
        } ?: ""
        val toSql = filter.toCreatedAt?.takeIf { it.isNotBlank() }?.let {
            " AND created_at < :toCreatedAt"
        } ?: ""
        val qualifiedTable = when (dialect) {
            FeedSqlDialect.PostgreSQL -> "account_$accountPrefix.$table"
            FeedSqlDialect.MySQL -> "$database.${accountPrefix}_$table"
        }
        return "SELECT * FROM (SELECT ${projection(type, dialect)} FROM $qualifiedTable " +
            "WHERE TRUE$userSql$searchSql$worldSql$fromSql$toSql$typeSql$cursorSql ORDER BY created_at DESC, id DESC " +
            "LIMIT ${filter.limit.coerceIn(1, 100)}) source_${type.name}"
    }

    private fun projection(type: FeedType, dialect: FeedSqlDialect): String {
        val nullText = if (dialect == FeedSqlDialect.PostgreSQL) {
            "CAST(NULL AS TEXT)"
        } else {
            "CAST(NULL AS CHAR)"
        }
        val values = MutableList(22) { nullText }
        // GPS 和 Online/Offline 的 time 列是 BIGINT，其他分支的空值也必须保持同一类型。
        values[8] = if (dialect == FeedSqlDialect.PostgreSQL) "CAST(NULL AS BIGINT)" else "CAST(NULL AS SIGNED)"
        values[0] = "id"; values[1] = "created_at"; values[2] = "user_id"; values[3] = "display_name"
        values[4] = if (type == FeedType.Online || type == FeedType.Offline) "type" else "'${type.name}'"
        when (type) {
            FeedType.GPS -> listOf("location", "world_name", "previous_location", "time", "group_name").forEachIndexed { i, v -> values[5 + i] = v }
            FeedType.Online, FeedType.Offline -> {
                values[5] = "location"
                values[6] = "world_name"
                values[8] = "time"
                values[9] = "group_name"
            }
            FeedType.Status -> listOf("status", "status_description", "previous_status", "previous_status_description").forEachIndexed { i, v -> values[10 + i] = v }
            FeedType.Bio -> listOf("bio", "previous_bio").forEachIndexed { i, v -> values[14 + i] = v }
            FeedType.Avatar -> listOf("owner_id", "avatar_name", "current_avatar_image_url", "current_avatar_thumbnail_image_url", "previous_current_avatar_image_url", "previous_current_avatar_thumbnail_image_url").forEachIndexed { i, v -> values[16 + i] = v }
        }
        return values.mapIndexed { i, value -> "$value AS c$i" }.joinToString(", ")
    }
}
