package io.github.vrcmteam.vrcm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vrcmteam.vrcm.presentation.vrcx.VrcxFeedEvent
import io.github.vrcmteam.vrcm.presentation.vrcx.VrcxMobileScreen

/** VRCX Mobile 的只读入口，不初始化 VRChat 登录、WebSocket 或后台采集。 */
@Composable
fun App(
    windowChrome: @Composable () -> Unit = {},
    officialLinkInbox: Any? = null,
    notificationLaunchInbox: Any? = null,
    previewEvents: List<VrcxFeedEvent>? = null,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            windowChrome()
            VrcxMobileScreen(previewEvents = previewEvents)
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val ignored = officialLinkInbox to notificationLaunchInbox
}
