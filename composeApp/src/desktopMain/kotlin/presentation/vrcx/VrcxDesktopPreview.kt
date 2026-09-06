@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * 桌面 UI 预览样本。仅当以 VRCX_PREVIEW=1 启动时注入，
 * 用于在未接入数据库前查看卡片与筛选效果；正常浏览路径不引用本文件。
 */
fun desktopPreviewFeed(): List<VrcxFeedEvent> = listOf(
    VrcxFeedEvent(
        rowId = 101, createdAt = isoAgo(3), userId = "usr_1024a", displayName = "Kipfel",
        type = FeedType.Online, worldName = "The Black Cat", location = "wrld_4432ea09b6~private(usr_1024a)~region(us)", groupName = null,
    ),
    VrcxFeedEvent(
        rowId = 102, createdAt = isoAgo(5), userId = "usr_8f3b2c", displayName = "Luo",
        type = FeedType.Status, status = "online", statusDescription = "探索新世界 ing",
        previousStatus = "join me", previousStatusDescription = null,
    ),
    VrcxFeedEvent(
        rowId = 103, createdAt = isoAgo(11), userId = "usr_7a2e0f", displayName = "yomogi",
        type = FeedType.GPS, worldName = "The Great Pug", previousLocation = "~hidden",
        location = "wrld_ba1a5d2a~group(grp_2c1f03)~region(jp)", groupName = "Mogura 例会",
        time = null,
    ),
    VrcxFeedEvent(
        rowId = 104, createdAt = isoAgo(26), userId = "usr_c51d9a", displayName = "Yuki",
        type = FeedType.Avatar, ownerId = "avtr_3e92f1c0", avatarName = "Mikan Runner v3",
        currentAvatarImageUrl = null, currentAvatarThumbnailImageUrl = null,
    ),
    VrcxFeedEvent(
        rowId = 105, createdAt = isoAgo(52), userId = "usr_8f3b2c", displayName = "Luo",
        type = FeedType.Offline, worldName = null, location = null, groupName = null,
    ),
    VrcxFeedEvent(
        rowId = 106, createdAt = isoAgo(2, 41), userId = "usr_b9071e", displayName = "Sora",
        type = FeedType.Bio, bio = "近期沉迷 3D 建模，欢迎交流", previousBio = "路过的普通玩家",
    ),
    VrcxFeedEvent(
        rowId = 107, createdAt = isoAgo(3, 18), userId = "usr_1024a", displayName = "Kipfel",
        type = FeedType.GPS, worldName = "Midnight Bar", previousLocation = "The Black Cat",
        location = "wrld_77c1a2b3~nonce(23333)~region(us)", groupName = "Kipfel Lounge",
        time = null,
    ),
    VrcxFeedEvent(
        rowId = 108, createdAt = isoAgo(5, 9), userId = "usr_4e10c8", displayName = "Neko",
        type = FeedType.Status, status = "ask me", statusDescription = "求带新地图",
        previousStatus = "busy", previousStatusDescription = null,
    ),
    VrcxFeedEvent(
        rowId = 109, createdAt = isoAgo(9, 33), userId = "usr_c51d9a", displayName = "Yuki",
        type = FeedType.Online, worldName = "Space Junk Yard", location = "wrld_12ab34cd~private(usr_c51d9a)~region(eu)", groupName = null,
    ),
    VrcxFeedEvent(
        rowId = 110, createdAt = isoAgo(13, 0), userId = "usr_7a2e0f", displayName = "yomogi",
        type = FeedType.Avatar, ownerId = "avtr_77b0d4e5", avatarName = "Cloaked Wanderer",
        currentAvatarImageUrl = null, currentAvatarThumbnailImageUrl = null,
    ),
    VrcxFeedEvent(
        rowId = 111, createdAt = isoAgo(1, 46), userId = "usr_b9071e", displayName = "Sora",
        type = FeedType.Offline, worldName = "Midnight Bar", location = null, groupName = null,
    ),
    VrcxFeedEvent(
        rowId = 112, createdAt = isoAgo(2, 2), userId = "usr_4e10c8", displayName = "Neko",
        type = FeedType.Bio, bio = null, previousBio = null,
    ),
)

private fun isoAgo(hoursAgo: Long, minutesAgo: Long = 0): String {
    val now = Clock.System.now()
    return (now - hoursAgo.hours - minutesAgo.minutes).toString()
}
