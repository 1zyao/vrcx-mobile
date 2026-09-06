package io.github.vrcmteam.vrcm.presentation.vrcx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun VrcxConnectionEditor(
    initialConfig: RemoteDatabaseConfig?,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var host by remember(initialConfig) { mutableStateOf(initialConfig?.host.orEmpty()) }
    var port by remember(initialConfig) { mutableStateOf(initialConfig?.port?.toString() ?: "5432") }
    var database by remember(initialConfig) { mutableStateOf(initialConfig?.database.orEmpty()) }
    var username by remember(initialConfig) { mutableStateOf(initialConfig?.username.orEmpty()) }
    var password by remember(initialConfig) { mutableStateOf(initialConfig?.password.orEmpty()) }
    var accountPrefix by remember(initialConfig) { mutableStateOf(initialConfig?.accountPrefix.orEmpty()) }
    var tls by remember(initialConfig) { mutableStateOf(initialConfig?.tls ?: true) }
    var databaseType by remember(initialConfig) {
        mutableStateOf(initialConfig?.databaseType ?: RemoteDatabaseType.PostgreSQL)
    }
    var databaseTypeExpanded by remember { mutableStateOf(false) }
    var accountDialogOpen by remember { mutableStateOf(false) }
    var accountPrefixes by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingConfig by remember { mutableStateOf<RemoteDatabaseConfig?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }

    fun readConfig(requireAccount: Boolean = true): RemoteDatabaseConfig? {
        val parsedPort = port.toIntOrNull()
        return when {
            host.isBlank() -> { feedback = "请输入数据库地址"; null }
            parsedPort !in 1..65535 -> { feedback = "端口必须是 1 到 65535"; null }
            database.isBlank() -> { feedback = "请输入数据库名称"; null }
            username.isBlank() -> { feedback = "请输入用户名"; null }
            requireAccount && accountPrefix.isBlank() -> { feedback = "请先发现并选择 VRCX 账号"; null }
            accountPrefix.isNotBlank() && !accountPrefix.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) -> {
                feedback = "账号前缀只能包含字母、数字和下划线，且不能以数字开头"
                null
            }
            else -> RemoteDatabaseConfig(
                host = host,
                port = parsedPort!!,
                database = database,
                username = username,
                password = password,
                accountPrefix = accountPrefix,
                tls = tls,
                databaseType = databaseType,
            )
        }
    }

    suspend fun discoverAccounts(candidate: RemoteDatabaseConfig): List<String> {
        val client = createVrcxDatabaseClient(candidate)
        return if (candidate.databaseType == RemoteDatabaseType.PostgreSQL) {
            client.query(
                "SELECT nspname FROM pg_namespace WHERE nspname LIKE :pattern ESCAPE '\\' ORDER BY nspname",
                mapOf("pattern" to "account\\_%"),
            ).mapNotNull { row -> extractAccountPrefix(row.firstOrNull()?.toString(), "^account_([A-Za-z_][A-Za-z0-9_]*)$") }
                .distinct().sorted()
        } else {
            client.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = :database ORDER BY table_name",
                mapOf("database" to candidate.database),
            ).mapNotNull { row -> extractAccountPrefix(row.firstOrNull()?.toString(), "^([A-Za-z_][A-Za-z0-9_]*)_feed_(gps|status|bio|avatar|online_offline)$") }
                .distinct().sorted()
        }
    }

    fun saveConfig(candidate: RemoteDatabaseConfig) {
        runCatching { saveVrcxConnectionConfig(candidate) }
            .onSuccess { onSaved() }
            .onFailure {
                feedback = it.message ?: "保存失败"
                feedbackIsError = true
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("远程数据库连接", style = MaterialTheme.typography.headlineSmall)
        Text(
            "填写 VRCX-K 使用的远程数据库。建议使用只读账号；密码会由当前平台的安全存储保护。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { databaseTypeExpanded = true }) {
                Text("数据库类型：${databaseTypeLabel(databaseType)}")
            }
            DropdownMenu(
                expanded = databaseTypeExpanded,
                onDismissRequest = { databaseTypeExpanded = false },
            ) {
                RemoteDatabaseType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(databaseTypeLabel(type)) },
                        onClick = {
                            databaseType = type
                            accountPrefix = ""
                            accountPrefixes = emptyList()
                            databaseTypeExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("主机地址") }, singleLine = true)
        OutlinedTextField(
            port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
            label = { Text("端口") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(database, { database = it }, Modifier.fillMaxWidth(), label = { Text("数据库名称") }, singleLine = true)
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("用户名") }, singleLine = true)
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            if (databaseType == RemoteDatabaseType.PostgreSQL) {
                "点击保存配置时自动读取 account_<前缀> schema"
            } else {
                "点击保存配置时自动读取 <前缀>_feed_* 表"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = tls, onCheckedChange = { tls = it })
            Text("使用 TLS（推荐）")
        }
        if (databaseType == RemoteDatabaseType.MySQL) {
            Text(
                "MySQL/MariaDB 使用只读连接，表名格式为 <前缀>_feed_*。",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        feedback?.let {
            Text(
                it,
                color = if (feedbackIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = !isTesting,
                onClick = {
                val candidate = readConfig() ?: return@OutlinedButton
                    scope.launch {
                        isTesting = true
                        feedback = null
                        feedbackIsError = false
                        try {
                            FeedRepository(createVrcxDatabaseClient(candidate), candidate)
                                .load(FeedFilter(limit = 1))
                            feedback = "连接成功，Feed 只读查询可用"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (throwable: Throwable) {
                            feedback = throwable.message ?: throwable::class.simpleName ?: "连接失败"
                            feedbackIsError = true
                        } finally {
                            isTesting = false
                        }
                    }
                },
            ) {
                if (isTesting) CircularProgressIndicator() else Text("测试连接")
            }
            Button(
                enabled = !isDiscovering && !isTesting,
                onClick = {
                    val candidate = readConfig(requireAccount = false) ?: return@Button
                    scope.launch {
                        isDiscovering = true
                        feedback = null
                        feedbackIsError = false
                        try {
                            accountPrefixes = discoverAccounts(candidate)
                            if (accountPrefixes.isEmpty()) {
                                feedback = "未发现可用的 VRCX 账号，请确认只读用户有元数据权限"
                                feedbackIsError = true
                            } else if (accountPrefixes.size == 1) {
                                accountPrefix = accountPrefixes.single()
                                saveConfig(candidate.copy(accountPrefix = accountPrefix))
                            } else {
                                accountPrefix = ""
                                pendingConfig = candidate
                                accountDialogOpen = true
                                feedback = "发现多个账号，请选择一个"
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (throwable: Throwable) {
                            feedback = throwable.message ?: throwable::class.simpleName ?: "保存失败"
                            feedbackIsError = true
                        } finally {
                            isDiscovering = false
                        }
                    }
                },
            ) { Text("保存配置") }
        }
    }

    if (accountDialogOpen) {
        AlertDialog(
            onDismissRequest = { accountDialogOpen = false },
            title = { Text("选择 VRCX 账号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accountPrefixes.forEach { prefix ->
                        OutlinedButton(
                            onClick = {
                                accountPrefix = prefix
                                accountDialogOpen = false
                                pendingConfig?.let { candidate ->
                                    pendingConfig = null
                                    saveConfig(candidate.copy(accountPrefix = prefix))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(prefix) }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun extractAccountPrefix(value: String?, pattern: String): String? =
    value?.let { Regex(pattern).matchEntire(it)?.groupValues?.getOrNull(1) }

private fun databaseTypeLabel(type: RemoteDatabaseType): String = when (type) {
    RemoteDatabaseType.PostgreSQL -> "PostgreSQL"
    RemoteDatabaseType.MySQL -> "MySQL"
}
