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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

private const val DATABASE_OPERATION_TIMEOUT_MILLIS = 20_000L

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
    val locale = strings

    fun readConfig(requireAccount: Boolean = true): RemoteDatabaseConfig? {
        val parsedPort = port.toIntOrNull()
        return when {
            host.isBlank() -> { feedback = locale.vrcxMissingHost; null }
            parsedPort !in 1..65535 -> { feedback = locale.vrcxInvalidPort; null }
            database.isBlank() -> { feedback = locale.vrcxMissingDatabase; null }
            username.isBlank() -> { feedback = locale.vrcxMissingUsername; null }
            requireAccount && accountPrefix.isBlank() -> { feedback = locale.vrcxMissingAccount; null }
            accountPrefix.isNotBlank() && !accountPrefix.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) -> {
                feedback = locale.vrcxInvalidPrefix
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
        return withContext(Dispatchers.Default) {
            val client = createVrcxDatabaseClient(candidate)
            if (candidate.databaseType == RemoteDatabaseType.PostgreSQL) {
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
    }

    fun saveConfig(candidate: RemoteDatabaseConfig) {
        runCatching { saveVrcxConnectionConfig(candidate) }
            .onSuccess { onSaved() }
            .onFailure {
                feedback = it.message ?: locale.vrcxSaveFailed
                feedbackIsError = true
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(locale.vrcxConnectionTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            locale.vrcxConnectionDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { databaseTypeExpanded = true }) {
                Text(locale.vrcxDatabaseType.replace("%s", databaseTypeLabel(databaseType)))
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
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text(locale.vrcxHost) }, singleLine = true)
        OutlinedTextField(
            port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
            label = { Text(locale.vrcxPort) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(database, { database = it }, Modifier.fillMaxWidth(), label = { Text(locale.vrcxDatabase) }, singleLine = true)
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(locale.authLoginUsername) }, singleLine = true)
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text(locale.authLoginPassword) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            if (databaseType == RemoteDatabaseType.PostgreSQL) {
                locale.vrcxDiscoverSchema.replace("%s", "<prefix>")
            } else {
                locale.vrcxDiscoverTables.replace("%s", "<prefix>")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = tls, onCheckedChange = { tls = it })
            Text(locale.vrcxTls)
        }
        if (databaseType == RemoteDatabaseType.MySQL) {
            Text(
                locale.vrcxMysqlDescription.replace("%s", "<prefix>"),
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
                            withTimeout(DATABASE_OPERATION_TIMEOUT_MILLIS) {
                                withContext(Dispatchers.Default) {
                                    FeedRepository(createVrcxDatabaseClient(candidate), candidate)
                                        .load(FeedFilter(limit = 1))
                                }
                            }
                            feedback = locale.vrcxConnectionSucceeded
                        } catch (_: TimeoutCancellationException) {
                            feedback = locale.vrcxTimeout
                            feedbackIsError = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (throwable: Throwable) {
                            feedback = throwable.message ?: throwable::class.simpleName ?: locale.vrcxConnectionFailed
                            feedbackIsError = true
                        } finally {
                            isTesting = false
                        }
                    }
                },
            ) {
                if (isTesting) CircularProgressIndicator() else Text(locale.vrcxTestConnection)
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
                            accountPrefixes = withTimeout(DATABASE_OPERATION_TIMEOUT_MILLIS) {
                                discoverAccounts(candidate)
                            }
                            if (accountPrefixes.isEmpty()) {
                                feedback = locale.vrcxNoAccounts
                                feedbackIsError = true
                            } else if (accountPrefixes.size == 1) {
                                accountPrefix = accountPrefixes.single()
                                saveConfig(candidate.copy(accountPrefix = accountPrefix))
                            } else {
                                accountPrefix = ""
                                pendingConfig = candidate
                                accountDialogOpen = true
                                feedback = locale.vrcxMultipleAccounts
                            }
                        } catch (_: TimeoutCancellationException) {
                            feedback = locale.vrcxTimeout
                            feedbackIsError = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (throwable: Throwable) {
                            feedback = throwable.message ?: throwable::class.simpleName ?: locale.vrcxSaveFailed
                            feedbackIsError = true
                        } finally {
                            isDiscovering = false
                        }
                    }
                },
            ) { Text(locale.vrcxSaveConfiguration) }
        }
    }

    if (accountDialogOpen) {
        AlertDialog(
            onDismissRequest = { accountDialogOpen = false },
            title = { Text(locale.vrcxSelectAccount) },
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
