package io.github.vrcmteam.vrcm.presentation.vrcx

/** VRCX 五张 feed 表映射后的统一只读模型。 */
data class VrcxFeedEvent(
    val rowId: Long,
    val createdAt: String,
    val userId: String,
    val displayName: String,
    val type: FeedType,
    val location: String? = null,
    val worldName: String? = null,
    val previousLocation: String? = null,
    val time: Long? = null,
    val groupName: String? = null,
    val status: String? = null,
    val statusDescription: String? = null,
    val previousStatus: String? = null,
    val previousStatusDescription: String? = null,
    val bio: String? = null,
    val previousBio: String? = null,
    val ownerId: String? = null,
    val avatarName: String? = null,
    val currentAvatarImageUrl: String? = null,
    val currentAvatarThumbnailImageUrl: String? = null,
    val previousCurrentAvatarImageUrl: String? = null,
    val previousCurrentAvatarThumbnailImageUrl: String? = null,
)

enum class FeedType { GPS, Status, Bio, Avatar, Online, Offline }

/** 将 VRCX 的 location 编码转换成适合手机显示的世界和实例摘要。 */
data class FeedLocationDisplay(
    val worldId: String,
    val instanceId: String?,
)

fun parseFeedLocation(location: String?): FeedLocationDisplay? {
    val value = location?.trim().orEmpty()
    if (!value.startsWith("wrld_")) return null
    val separator = value.indexOf(':')
    val worldId = if (separator >= 0) value.substring(0, separator) else value
    if (!worldId.matches(Regex("wrld_[A-Za-z0-9-]+"))) return null
    val instance = value.substringAfter(':', "").substringBefore('~').takeIf { it.isNotBlank() }
    return FeedLocationDisplay(worldId, instance)
}

data class FeedCursor(val createdAt: String, val rowId: Long)

data class FeedFilter(
    val types: Set<FeedType> = FeedType.entries.toSet(),
    val userId: String? = null,
    val worldName: String? = null,
    val query: String = "",
    val fromCreatedAt: String? = null,
    val toCreatedAt: String? = null,
    val cursor: FeedCursor? = null,
    val limit: Int = 40,
)
