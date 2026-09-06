package io.github.vrcmteam.vrcm.presentation.vrcx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTextField
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import org.koin.compose.koinInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 只读浏览首页：真实连接远程 PostgreSQL 并分页展示 VRCX Feed，绝不生成示例日志。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VrcxMobileScreen(
    previewEvents: List<VrcxFeedEvent>? = null,
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
    val usersApi: UsersApi = koinInject()
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
    LaunchedEffect(events, isPreview) {
        if (isPreview || SharedFlowCentre.currentSession.value == null) return@LaunchedEffect
        val missingIds = events.asSequence()
            .map(VrcxFeedEvent::userId)
            .filter { it.isNotBlank() && it !in userIcons }
            .distinct()
            .toList()
        if (missingIds.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            missingIds.mapNotNull { userId ->
                runCatching { userId to usersApi.fetchUser(userId).iconUrl }.getOrNull()
            }.toMap()
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    hasMore -> OutlinedButton(
                                        onClick = { runLoad(reset = false) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(strings.vrcxLoadMore) }
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
        val previousLocation = parseFeedLocation(event.previousLocation)
        FeedEventText(
            headline = buildString {
                append(locale.vrcxLocationChanged)
                if (event.worldName != null) append(" · ${event.worldName}")
            },
            subtitle = when {
                location != null && previousLocation != null && location.worldId != previousLocation.worldId ->
                    "${previousLocation.worldId} → ${location.worldId}"
                location != null -> "${locale.vrcxEnter} ${location.worldId}"
                else -> null
            },
            meta = listOfNotNull(
                event.groupName?.let { "${locale.vrcxGroup} $it" },
                event.worldName?.let { "${locale.vrcxWorld}：$it" },
                location?.instanceId?.let { "${locale.vrcxRoom} ${it.take(12)}" },
            ),
        )
    }
    FeedType.Online, FeedType.Offline -> FeedEventText(
        headline = if (event.type == FeedType.Online) locale.vrcxOnlineEvent else locale.vrcxOfflineEvent,
         subtitle = event.worldName?.let { "${locale.vrcxLocatedIn}：$it" },
        meta = listOfNotNull(
            event.worldName?.let { "${locale.vrcxWorld}：$it" },
            parseFeedLocation(event.location)?.instanceId?.let { "${locale.vrcxRoom} ${it.take(12)}" },
            event.groupName?.let { "${locale.vrcxGroup} $it" },
        ),
    )
    FeedType.Status -> FeedEventText(
         headline = event.status ?: locale.vrcxStatusChanged,
        subtitle = when {
            event.statusDescription != null -> event.statusDescription
            event.previousStatus != null && event.previousStatus != event.status ->
                locale.vrcxOriginalStatus.replace("%s", event.previousStatus)
            else -> null
        },
        meta = emptyList(),
    )
    FeedType.Bio -> FeedEventText(
         headline = locale.vrcxBioChanged,
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
                UserStateIcon(
                    iconUrl = userIcon,
                    modifier = Modifier.size(42.dp),
                )
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
                Text(text.headline, style = MaterialTheme.typography.bodyLarge)
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
