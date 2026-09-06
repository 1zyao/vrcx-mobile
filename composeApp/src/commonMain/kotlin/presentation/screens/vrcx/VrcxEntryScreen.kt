package io.github.vrcmteam.vrcm.presentation.screens.vrcx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.vrcx.VrcxMobileScreen
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.serialization.Serializable

@Serializable
object VrcxEntryScreen : AppRoute {
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("VRCM", style = MaterialTheme.typography.displaySmall)
            Text(strings.vrcxChooseUsage, style = MaterialTheme.typography.titleMedium)
            Button(onClick = { navigator replace AuthAnimeScreen(false) }) {
                Text(strings.vrcxLoginVrchat)
            }
            OutlinedButton(onClick = { navigator replace VrcxBrowseScreen }) {
                Text(strings.vrcxBrowseDatabase)
            }
        }
    }
}

@Serializable
object VrcxBrowseScreen : AppRoute {
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        VrcxMobileScreen(onExit = { navigator replace VrcxEntryScreen })
    }
}
