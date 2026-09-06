package io.github.vrcmteam.vrcm.presentation.vrcx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTextField
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import io.github.vrcmteam.vrcm.storage.UserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import org.koin.compose.koinInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MAX_CONCURRENT_USER_FETCH = 6

/** 只读浏览首页：真实连接远程 PostgreSQL 并分页展示 VRCX Feed，绝不生成示例日志。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VrcxMobileScreen(    previewEvents: List<VrcxFeedEvent>? = null,
    onExit: (() -> Unit)? = null,
    embeddedInHome: Boolean = false,
) {
    val isPreview = previewEvents != null
    val scope = rememberCoroutineScope()
    var repository by remember { mutableStateOf<FeedRepository?>(null) }
    var events by remember { mutableStateOf<List<VrcxFeedEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(FeedFilter()) }
    var searchText by remember { mutableStateOf("") }
    var userIcons by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val listState = rememberLazyListState()
    val usersApi: UsersApi = koinInject()
    val userProfileCacheStore: UserProfileCacheStore = koinInject()
    val locale = strings

    /** 预览模式使用与远程查询一致的包含式筛选；正常模式使用远端查询结果。 */
    val shownEvents = if (previewEvents != null) {
        previewEvents.filter { it.matchesFeedFilter(filter) }
    } else {
        events
    }

    fun runLoad(reset: Boolean, requestedFilter: FeedFilter = filter) {
        if (isLoading || isPreview) return
        scope.launch {
            isLoading = true
            if (reset) error = null
            try {
                val cfg = loadVrcxConnectionConfig()
                if (cfg == null) {
                    repository = null
                    events = emptyList()
                    hasMore = false
                    return@launch
                }
                if (repository == null) {
                    repository = FeedRepository(createVrcxDatabaseClient(cfg), cfg)
                }
                val cursor = if (reset) null
                else events.lastOrNull()?.let { FeedCursor(it.createdAt, it.rowId) }
                val page = withTimeout(20_000L) {
                    withContext(Dispatchers.Default) {
                        repository?.load(requestedFilter.copy(cursor = cursor)) ?: emptyList()
                    }
                }
                events = if (reset) page else events + page
                hasMore = page.size >= requestedFilter.limit
            } catch (_: TimeoutCancellationException) {
                error = locale.vrcxTimeout
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName ?: locale.unknown
            } finally {
                isLoading = false
            }
        }
    }

    val toggleType: (FeedType) -> Unit = { type ->
        val next = if (type in filter.types) filter.types - type else filter.types + type
        if (next.isNotEmpty()) {
            filter = filter.copy(types = next)
            if (!isPreview) runLoad(reset = true)
        }
    }

    fun submitSearch() {
        val next = filter.copy(query = searchText.trim(), cursor = null)
        filter = next
        if (!isPreview) runLoad(reset = true, requestedFilter = next)
    }

    LaunchedEffect(Unit) { if (!isPreview) runLoad(reset = true) }
    LaunchedEffect(listState, isPreview) {
        if (isPreview) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex to layoutInfo.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisibleIndex, totalItemsCount) ->
            if (hasMore && !isLoading && totalItemsCount > 0 &&
                lastVisibleIndex >= totalItemsCount - 3
            ) {
                runLoad(reset = false)
            }
        }
    }
    LaunchedEffect(events, isPreview) {
        val session = SharedFlowCentre.currentSession.value
        if (isPreview || session == null) return@LaunchedEffect
        val ownerUserId = session.account.userId
        val allIds = events.asSequence()
            .map(VrcxFeedEvent::userId)
            .filter { it.isNotBlank() && it !in userIcons }
            .distinct()
            .toList()
        if (allIds.isEmpty()) return@LaunchedEffect
        // 优先读本地资料缓存，命中的头像秒出，只有缺失的才补拉网络
        val cached = withContext(Dispatchers.Default) {
            allIds.mapNotNull { userId ->
                runCatching {
                    userProfileCacheStore.load(ownerUserId, userId)
                        ?.user?.iconUrl?.let { userId to it }
                }.getOrNull()
            }.toMap()
        }
        if (cached.isNotEmpty()) userIcons = userIcons + cached
        val missingIds = allIds.filter { it !in userIcons }
        if (missingIds.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.Default) {
            // 参考 VRCX 桌面端限流思路：并发拉取，避免打满 VRChat API 触发 429 退避
            val gate = Semaphore(MAX_CONCURRENT_USER_FETCH)
            coroutineScope {
                missingIds.map { userId ->
                    async {
                        gate.withPermit {
                            runCatching {
                                val user = usersApi.fetchUser(userId)
                                userProfileCacheStore.save(ownerUserId, userId, UserProfileCache(user = user))
                                userId to user.iconUrl
                            }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        }
        if (loaded.isNotEmpty()) userIcons = userIcons + loaded
    }

    Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = if (embeddedInHome) getInsetPadding(WindowInsets::getTop) + 80.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onExit != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.vrcxBack)
                    }
                    Text(strings.vrcxFeedTitle, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { runLoad(reset = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.vrcxRefresh)
                    }
                }
            }
            if (isPreview) {
                PreviewBanner()
            }
            SearchTextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchText,
                onSearch = ::submitSearch,
                onValueChange = { searchText = it },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = { toggleType(type) },
                        label = { Text(typeLabel(type, strings)) },
                    )
                }
            }
            RefreshBox(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                isRefreshing = isLoading && !isPreview,
                doRefresh = { runLoad(reset = true) },
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom = getInsetPadding(12, WindowInsets::getBottom) + 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        shownEvents.isEmpty() && isLoading -> item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        shownEvents.isEmpty() && error != null -> item {
                            ErrorCard(message = error.orEmpty(), onRetry = { runLoad(reset = true) })
                        }
                        shownEvents.isEmpty() -> item { EmptyFeedCard() }
                        else -> {
                            items(shownEvents, key = { "${it.type.name}-${it.createdAt}-${it.rowId}" }) { event ->
                        FeedEventCard(event, userIcons[event.userId], locale)
                            }
                            item {
                                when {
                                    isPreview -> Unit
                                    isLoading -> Box(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                    hasMore -> Unit
                                    else -> Text(
                                        strings.vrcxEndOfFeed,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}

private fun VrcxFeedEvent.matchesFeedFilter(filter: FeedFilter): Boolean {
    if (type !in filter.types) return false
    val user = filter.userId?.trim()?.takeIf { it.isNotEmpty() }
    if (user != null && !userId.contains(user, ignoreCase = true)) return false
    val world = filter.worldName?.trim()?.takeIf { it.isNotEmpty() }
    if (world != null && !worldName.orEmpty().contains(world, ignoreCase = true)) return false
    val from = filter.fromCreatedAt?.trim()?.takeIf { it.isNotEmpty() }
    if (from != null && createdAt < from) return false
    val to = filter.toCreatedAt?.trim()?.takeIf { it.isNotEmpty() }
    if (to != null && createdAt >= to) return false

    val query = filter.query.trim()
    if (query.isEmpty()) return true
    val values = if (query.startsWith("wrld_", ignoreCase = true) || query.startsWith("grp_", ignoreCase = true)) {
        listOf(location)
    } else {
        when (type) {
            FeedType.GPS, FeedType.Online, FeedType.Offline ->
                listOf(displayName, worldName, groupName)
            FeedType.Status -> listOf(displayName, status, statusDescription)
            FeedType.Bio -> listOf(displayName, bio)
            FeedType.Avatar -> listOf(displayName, avatarName)
        }
    }
    return values.any { it?.contains(query, ignoreCase = true) == true }
}

@Composable
private fun PreviewBanner() {
    val locale = strings
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(locale.vrcxPreviewTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                locale.vrcxPreviewDescription,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    val locale = strings
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CloudOff, contentDescription = null)
            Text(locale.vrcxQueryFailed, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text(locale.retry) }
        }
    }
}

@Composable
private fun EmptyFeedCard() {
    val locale = strings
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(locale.vrcxNoRecords, style = MaterialTheme.typography.titleMedium)
            Text(
                locale.vrcxNoRecordsDescription,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun typeLabel(type: FeedType, locale: LocaleStrings): String = when (type) {
    FeedType.GPS -> locale.vrcxLocationChanged
    FeedType.Status -> locale.vrcxStatusChanged
    FeedType.Bio -> locale.vrcxBioChanged
    FeedType.Avatar -> locale.vrcxAvatarChanged
    FeedType.Online -> locale.vrcxOnline
    FeedType.Offline -> locale.vrcxOffline
}

/** 事件类型的图标与强调色集中在此，跟随 FeedType 语义而非业务数据。 */
private object FeedTypeStyle {
    fun icon(type: FeedType): ImageVector = when (type) {
        FeedType.GPS -> Icons.Filled.Place
        FeedType.Status -> Icons.Filled.Face
        FeedType.Bio -> Icons.Filled.Edit
        FeedType.Avatar -> Icons.Filled.Portrait
        FeedType.Online -> Icons.Filled.Power
        FeedType.Offline -> Icons.Filled.PowerOff
    }

    fun color(type: FeedType): Color = when (type) {
        FeedType.GPS -> Color(0xFF1E88E5)
        FeedType.Status -> Color(0xFF8E24AA)
        FeedType.Bio -> Color(0xFFF4511E)
        FeedType.Avatar -> Color(0xFFD81B60)
        FeedType.Online -> Color(0xFF43A047)
        FeedType.Offline -> Color(0xFF757575)
    }
}

@OptIn(ExperimentalTime::class)
private fun formatFeedTime(createdAt: String): String =
    runCatching {
        Instant.parse(createdAt)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .ignoredFormat
    }.getOrDefault(createdAt)

/** 按类型生成动态描述：headline 为主句，subtitle 为补充句，meta 为次要信息行。 */
private data class FeedEventText(val headline: String, val subtitle: String?, val meta: List<String>)

private fun describe(event: VrcxFeedEvent, locale: LocaleStrings): FeedEventText = when (event.type) {
    FeedType.GPS -> {
        val location = parseFeedLocation(event.location)
        FeedEventText(
            headline = locale.vrcxLocationChanged,
            subtitle = when {
                location?.access == FeedLocationAccess.Private -> locale.vrcxPrivateRoom
                event.worldName != null -> locale.vrcxCurrentLocation.replace("%s", locationText(event, locale))
                else -> null
            },
            meta = listOfNotNull(
                location?.instanceId?.let { "${locale.vrcxRoom} ${it.take(12)}" },
            ),
        )
    }
    FeedType.Online -> FeedEventText(
        headline = event.worldName?.let { locale.vrcxOnlineAt.replace("%s", locationText(event, locale)) }
            ?: locale.vrcxOnlineEvent,
        subtitle = null,
        meta = listOfNotNull(
            parseFeedLocation(event.location)?.instanceId?.let { "${locale.vrcxRoom} ${it.take(12)}" },
        ),
    )
    FeedType.Offline -> FeedEventText(
        headline = locale.vrcxOfflineEvent,
        subtitle = null,
        meta = emptyList(),
    )
    FeedType.Status -> FeedEventText(
        headline = locale.vrcxStatusChanged,
        subtitle = event.status?.let { status ->
            locale.vrcxStatusUpdate.replaceFirst("%s", status).replaceFirst("%s", event.statusDescription.orEmpty())
        } ?: event.statusDescription,
        meta = emptyList(),
    )
    FeedType.Bio -> FeedEventText(
        headline = locale.vrcxBioUpdate,
        subtitle = event.bio,
        meta = listOfNotNull(
            event.previousBio?.takeIf { it != event.bio }?.let { locale.vrcxOriginalBio.replace("%s", it) },
        ),
    )
    FeedType.Avatar -> FeedEventText(
         headline = event.avatarName?.let { locale.vrcxChangeModelTo.replace("%s", it) } ?: locale.vrcxChangeModel,
        subtitle = null,
         meta = listOfNotNull(event.ownerId?.let { locale.vrcxModelAuthor.replace("%s", it) }),
    )
}

private fun locationText(event: VrcxFeedEvent, locale: LocaleStrings): String {
    val location = parseFeedLocation(event.location)
    if (location?.access == FeedLocationAccess.Private) return locale.vrcxPrivateRoom
    val world = event.worldName ?: return locationAccessLabel(location?.access, locale)
    val access = locationAccessLabel(location?.access, locale)
    return buildString {
        append(world)
        append(" · ")
        append(access)
        event.groupName?.let { append(" (${locale.vrcxGroup} $it)") }
    }
}

private fun locationAccessLabel(access: FeedLocationAccess?, locale: LocaleStrings): String = when (access) {
    FeedLocationAccess.FriendsPlus -> locale.friendActivityAccessFriendsPlus
    FeedLocationAccess.Friends -> locale.friendActivityAccessFriends
    FeedLocationAccess.InvitePlus -> locale.friendActivityAccessInvitePlus
    FeedLocationAccess.Invite -> locale.friendActivityAccessInvite
    FeedLocationAccess.Group -> locale.friendActivityAccessGroup
    FeedLocationAccess.Private -> locale.vrcxPrivateRoom
    FeedLocationAccess.Unknown, null, FeedLocationAccess.Public -> locale.friendActivityAccessPublic
}

@Composable
private fun FeedEventCard(event: VrcxFeedEvent, userIcon: String?, locale: LocaleStrings) {
    val text = describe(event, locale)
    val accent = FeedTypeStyle.color(event.type)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                UserStateIcon(iconUrl = userIcon, modifier = Modifier.size(42.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatFeedTime(event.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.type == FeedType.Status) {
                    StatusChange(event)
                } else {
                    Text(text.headline, style = MaterialTheme.typography.bodyLarge)
                }
                if (event.type != FeedType.Status) {
                    text.subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (text.meta.isNotEmpty()) {
                        Text(
                            text.meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChange(event: VrcxFeedEvent) {
    val previous = event.previousStatus?.takeIf { it != event.status }
    val colorChanged = previous != null && statusColor(previous) != statusColor(event.status)
    val descriptionChanged = event.previousStatusDescription != event.statusDescription

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (colorChanged) {
                StatusDot(statusColor(previous))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusDot(statusColor(event.status))
            if (!colorChanged || descriptionChanged) {
                event.statusDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        modifier = Modifier.offset(y = (-1).dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (colorChanged && descriptionChanged) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                StatusDot(statusColor(previous))
                Text(
                    event.previousStatusDescription.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                StatusDot(statusColor(event.status))
                Text(
                    event.statusDescription.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!colorChanged && descriptionChanged && event.previousStatusDescription != null) {
            Text(
                event.previousStatusDescription,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
    }
}

private fun statusColor(status: String?): Color = GameColor.Status.fromValue(
    UserStatus.entries.firstOrNull { it.value.equals(status, ignoreCase = true) },
)
