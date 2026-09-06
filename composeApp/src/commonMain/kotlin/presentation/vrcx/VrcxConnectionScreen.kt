package io.github.vrcmteam.vrcm.presentation.vrcx

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalMaterial3Api::class)
object VrcxConnectionScreen : AppDetailRoute {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.vrcxConnectionTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.vrcxBack)
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                VrcxConnectionEditor(
                    initialConfig = loadVrcxConnectionConfig(),
                    onSaved = { navigator.pop() },
                )
            }
        }
    }
}
