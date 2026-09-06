package io.github.vrcmteam.vrcm.presentation.vrcx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 只读浏览首页：真实连接远程 PostgreSQL 并分页展示 VRCX Feed，绝不生成示例日志。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VrcxMobileScreen(previewEvents: List<VrcxFeedEvent>? = null) {
    val isPreview = previewEvents != null
    val scope = rememberCoroutineScope()
    val configLocation = remember { vrcxConnectionConfigLocation() }
    var config by remember { mutableStateOf<RemoteDatabaseConfig?>(null) }
    var repository by remember { mutableStateOf<FeedRepository?>(null) }
    var events by remember { mutableStateOf<List<VrcxFeedEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(FeedFilter()) }
    var showEditor by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

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
                    config = null
                    repository = null
                    events = emptyList()
                    hasMore = false
                    return@launch
                }
                if (config != cfg) {
                    config = cfg
                    repository = FeedRepository(createVrcxDatabaseClient(cfg), cfg)
                }
                val cursor = if (reset) null
                else events.lastOrNull()?.let { FeedCursor(it.createdAt, it.rowId) }
                val page = repository?.load(requestedFilter.copy(cursor = cursor)) ?: emptyList()
                events = if (reset) page else events + page
                hasMore = page.size >= requestedFilter.limit
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName ?: "未知错误"
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = if (showEditor) {
                    {
                        IconButton(onClick = { showEditor = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                } else {
                    {}
                },
                title = {
                    if (showEditor) {
                        Text("连接设置")
                    } else {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索 Feed") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                            singleLine = true,
                        )
                    }
                },
                actions = {
                    if (!showEditor) {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "连接设置")
                        }
                        IconButton(onClick = { runLoad(reset = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showEditor) {
                VrcxConnectionEditor(
                    initialConfig = config,
                    onSaved = {
                        showEditor = false
                        runLoad(reset = true)
                    },
                )
            } else if (isPreview) {
                PreviewBanner()
            } else {
                ConnectionStatusCard(
                    config = config,
                    configLocation = configLocation,
                    onOpenEditor = { showEditor = true },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = { toggleType(type) },
                        label = { Text(typeLabel(type)) },
                    )
                }
            }
            when {
                shownEvents.isEmpty() && isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                shownEvents.isEmpty() && error != null -> {
                    ErrorCard(message = error.orEmpty(), onRetry = { runLoad(reset = true) })
                }
                shownEvents.isEmpty() -> EmptyFeedCard()
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shownEvents, key = { "${it.type.name}-${it.createdAt}-${it.rowId}" }) { event ->
                        FeedEventCard(event)
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
                            ) { Text("加载更多") }
                            else -> Text(
                                "已到底部",
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("UI 预览模式", style = MaterialTheme.typography.titleMedium)
            Text(
                "以下为本地示例数据，仅用于预览卡片样式；接入数据库后本页面不包含任何示例内容。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    config: RemoteDatabaseConfig?,
    configLocation: String,
    onOpenEditor: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (config == null) {
                Text("尚未配置远程数据库", style = MaterialTheme.typography.titleMedium)
                Text(
                    "在应用内填写连接信息，保存后即可验证并浏览 VRCX-K 的只读 Feed。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenEditor) { Text("填写连接信息") }
                Text(configLocation, style = MaterialTheme.typography.labelSmall)
            } else {
                Text(
                    "已连接：${config.host}:${config.port}/${config.database}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "账号 account_${config.accountPrefix} · 只读查询",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onOpenEditor) { Text("修改连接") }
            }
            HorizontalDivider()
            Text(
                "VRCX-K 负责采集，VRCX Mobile 只执行固定 SELECT 浏览，不支持任意 SQL 与写操作。",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CloudOff, contentDescription = null)
            Text("查询失败", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyFeedCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("没有记录", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前筛选条件下没有匹配的 Feed 记录，或 VRCX-K 尚未采集到数据。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun typeLabel(type: FeedType): String = when (type) {
    FeedType.GPS -> "位置变动"
    FeedType.Status -> "状态变动"
    FeedType.Bio -> "简介变更"
    FeedType.Avatar -> "模型变动"
    FeedType.Online -> "上线"
    FeedType.Offline -> "下线"
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
private fun relativeTime(createdAt: String): String {
    val instant = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return createdAt
    val minutes = (Clock.System.now() - instant).inWholeMinutes
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}

/** 按类型生成动态描述：headline 为主句，subtitle 为补充句，meta 为次要信息行。 */
private data class FeedEventText(val headline: String, val subtitle: String?, val meta: List<String>)

private fun describe(event: VrcxFeedEvent): FeedEventText = when (event.type) {
    FeedType.GPS -> {
        val location = parseFeedLocation(event.location)
        val previousLocation = parseFeedLocation(event.previousLocation)
        FeedEventText(
            headline = buildString {
                append("位置变动")
                if (event.worldName != null) append(" · ${event.worldName}")
            },
            subtitle = when {
                location != null && previousLocation != null && location.worldId != previousLocation.worldId ->
                    "${previousLocation.worldId} → ${location.worldId}"
                location != null -> "进入 ${location.worldId}"
                else -> null
            },
            meta = listOfNotNull(
                event.groupName?.let { "群组 $it" },
                event.worldName?.let { "世界：$it" },
                location?.instanceId?.let { "房间 ${it.take(12)}" },
            ),
        )
    }
    FeedType.Online, FeedType.Offline -> FeedEventText(
        headline = if (event.type == FeedType.Online) "上线了" else "离线了",
        subtitle = event.worldName?.let { "所在世界：$it" },
        meta = listOfNotNull(
            event.worldName?.let { "世界：$it" },
            parseFeedLocation(event.location)?.instanceId?.let { "房间 ${it.take(12)}" },
            event.groupName?.let { "群组 $it" },
        ),
    )
    FeedType.Status -> FeedEventText(
        headline = event.status ?: "状态已更新",
        subtitle = when {
            event.statusDescription != null -> event.statusDescription
            event.previousStatus != null && event.previousStatus != event.status ->
                "原状态：${event.previousStatus}"
            else -> null
        },
        meta = emptyList(),
    )
    FeedType.Bio -> FeedEventText(
        headline = "简介变更",
        subtitle = event.bio,
        meta = listOfNotNull(
            event.previousBio?.takeIf { it != event.bio }?.let { "原简介：$it" },
        ),
    )
    FeedType.Avatar -> FeedEventText(
        headline = event.avatarName?.let { "更换模型为 $it" } ?: "更换模型",
        subtitle = null,
        meta = listOfNotNull(event.ownerId?.let { "模型作者 $it" }),
    )
}

@Composable
private fun FeedEventCard(event: VrcxFeedEvent) {
    val text = describe(event)
    val accent = FeedTypeStyle.color(event.type)
    val imageLoader = koinInject<ImageLoader>()
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (event.type == FeedType.Avatar && event.currentAvatarThumbnailImageUrl != null) {
                    AsyncImage(
                        model = event.currentAvatarThumbnailImageUrl,
                        imageLoader = imageLoader,
                        contentDescription = "模型缩略图",
                        modifier = Modifier.size(42.dp),
                    )
                } else {
                    Icon(
                        imageVector = FeedTypeStyle.icon(event.type),
                        contentDescription = typeLabel(event.type),
                        tint = accent,
                    )
                }
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
                        relativeTime(event.createdAt),
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
