package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import io.github.vrcmteam.vrcm.presentation.vrcx.VrcxMobileScreen
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

object VrcxPager : Pager {
    override val index: Int
        get() = 3

    override val title: String
        @Composable get() = strings.vrcxFeedTitle

    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.Storage)

    @Composable
    override fun Content() {
        VrcxMobileScreen(embeddedInHome = true)
    }
}
