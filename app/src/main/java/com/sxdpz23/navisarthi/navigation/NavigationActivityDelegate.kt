package com.sxdpz23.navisarthi.navigation

import android.app.PictureInPictureParams
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sxdpz23.navisarthi.navigation.core.data.MapLinkParser
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import kotlinx.coroutines.launch

/** Navigation behaviour owned by NaviSarthi's single Activity. */
class NavigationActivityDelegate(
    private val activity: ComponentActivity,
    private val vm: MapViewModel,
    private val onPhoneNavigationStarted: () -> Unit,
) {
    fun onCreate(handleStartupIntent: Boolean = true) {
        com.sxdpz23.navisarthi.navigation.ui.AppLocale.onLocaleChanged = { activity.recreate() }
        activity.lifecycleScope.launch {
            var last: Boolean? = null
            vm.state.collect { state ->
                if (state.navigating != last) {
                    last = state.navigating
                    if (state.navigating) onPhoneNavigationStarted()
                    runCatching { activity.setPictureInPictureParams(pipParams(state.navigating)) }
                }
            }
        }
        if (handleStartupIntent) handleIntent(activity.intent)
        vm.openStartView()
    }

    private fun pipParams(autoEnter: Boolean) = PictureInPictureParams.Builder()
        .setAspectRatio(android.util.Rational(3, 4))
        .setAutoEnterEnabled(autoEnter && com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.state.value.target == null)
        .setSeamlessResizeEnabled(false).build()

    fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            com.sxdpz23.navisarthi.navigation.ui.TripShortcut.ACTION -> com.sxdpz23.navisarthi.navigation.ui.TripShortcut.read(intent)?.let { (points, mode) -> vm.openTripShortcut(points, mode) }
            Intent.ACTION_VIEW -> {
                val text = intent.data?.toString() ?: return
                if (MapLinkParser.myMapId(text) != null || com.sxdpz23.navisarthi.navigation.core.data.ShortLinks.isShortener(text)) vm.openSharedText(text)
                else MapLinkParser.parse(text)?.let(vm::openDeepLink)
            }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::openSharedText)
        }
    }

    fun onStart() { com.sxdpz23.navisarthi.navigation.ui.AppVisibility.foreground.value = true }
    fun onStop() {
        com.sxdpz23.navisarthi.navigation.ui.AppVisibility.foreground.value = false
        com.sxdpz23.navisarthi.navigation.ui.StartView.saveLast(activity)
    }
    fun onResume() { com.sxdpz23.navisarthi.navigation.ui.Clock24.refresh(activity) }
    fun onPictureInPictureChanged(active: Boolean) { com.sxdpz23.navisarthi.navigation.ui.PipMode.active.value = active }
}
