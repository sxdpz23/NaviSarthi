package com.sxdpz23.navisarthi

import android.app.Application
import android.content.Context
import com.sxdpz23.navisarthi.navigation.core.diag.DiagLog
import com.sxdpz23.navisarthi.navigation.diag.CrashCatcher
import com.sxdpz23.navisarthi.navigation.ui.AppLocale
import com.sxdpz23.navisarthi.navigation.ui.Onboarding
import com.sxdpz23.navisarthi.navigation.ui.Traffic
import com.sxdpz23.navisarthi.navigation.ui.TransitLayer
import com.sxdpz23.navisarthi.navigation.ui.Units
import com.sxdpz23.navisarthi.navigation.ui.theme.AppTheme
import com.sxdpz23.navisarthi.navigation.ui.theme.DynamicColor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sxdpz23.navisarthi.data.DiagnosticsUploader
import com.sxdpz23.navisarthi.data.FirebaseFeatures
import com.sxdpz23.navisarthi.util.BuildId
import com.sxdpz23.navisarthi.util.CrashGuard
import com.sxdpz23.navisarthi.util.ExitInfoCollector
@dagger.hilt.android.HiltAndroidApp
class NaviSarthiApplication : Application(), coil.ImageLoaderFactory {
    @Inject lateinit var diag: DiagLog
    @Inject lateinit var http: okhttp3.OkHttpClient
    @Inject lateinit var locationProvider: com.sxdpz23.navisarthi.navigation.core.location.LocationProvider
    // Built here, before anything below asks for a link: the store applies the cached bundle's
    // pushed links as it is made, and the fonts and camera fetches at start read them.
    @Inject lateinit var calibrationStore: com.sxdpz23.navisarthi.navigation.core.config.CalibrationStore

    /** Coil with a HARD memory-cache cap. The default budget is ~25% of the app's heap CLASS,
     *  and largeHeap makes that class huge - on a 512 MB large heap Coil happily retains up to
     *  ~128 MB of decoded gallery bitmaps by design, which is most of the "rapid place churn
     *  runs into the ceiling" OOM (issue #182; measured: 3 gallery-bearing places grew the live
     *  Dalvik heap 14 -> 94 MB). 48 MB still holds a couple of screens of thumbnails + a hero
     *  or two; everything else re-decodes from Coil's disk cache, which is untouched. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        // The shared client (Google hosts go over Cronet and are counted), with Chrome's image
        // headers put on Google image requests first; Coil's own client said "okhttp/4.12.0".
        .okHttpClient { http.newBuilder().apply { interceptors().add(0, com.sxdpz23.navisarthi.navigation.core.net.GoogleTransport.imageHeaders) }.build() }
        .memoryCache {
            coil.memory.MemoryCache.Builder(this)
                .maxSizeBytes(if (com.sxdpz23.navisarthi.navigation.ui.MemoryPressure.lowRam) 16 * 1024 * 1024 else 48 * 1024 * 1024)
                .build()
        }
        .build()

    /** Apply the persisted in-app language to the Application context too (no-op when following the
     *  system), so `getString` from the ViewModel/nav-notification also localizes — resolved at launch
     *  from the saved pref (an in-session change re-reads it on next launch). */
    /**
     * Hand OS memory pressure to every holder that owns a large or native allocation (ported from
     * navisarthi-dpad, 2026-07-23). Before this existed nothing in the app implemented
     * ComponentCallbacks2, so a TRIM_MEMORY_COMPLETE released nothing at all and the OS had no
     * option but to kill us. Coil's own cache is trimmed here; everything else releases through
     * [com.sxdpz23.navisarthi.navigation.ui.MemoryPressure].
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.sxdpz23.navisarthi.navigation.ui.MemoryPressure.dispatch(level)
        com.sxdpz23.navisarthi.navigation.diag.TripNote.add("memory: the system asked for memory back (level $level)")
        if (com.sxdpz23.navisarthi.navigation.ui.MemoryPressure.isSevere(level)) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(com.sxdpz23.navisarthi.navigation.ui.AdaptiveDensity.wrap(base)))
    }

    override fun onCreate() {
        super.onCreate()
        // Hash the installed APK off-thread so the build identity is ready to stamp into ride logs.
        BuildId.warm(this)
        // Firebase is auto-initialized from google-services.json via the Google Services plugin.
        // Turn on the rest of the product suite (Analytics, Crashlytics, Performance, Remote
        // Config, App Check). No-op when no Firebase project is configured — the app stays local.
        FirebaseFeatures.init(this)
        // Install our uncaught-exception trace AFTER Firebase init so it chains to Crashlytics'
        // handler: every fatal lands in Crashlytics AND in a local crash log that uploads to
        // Firestore on the next launch — no crash goes untraced, even off the Crashlytics console.
        CrashGuard.install(this)
        // Capture WHY we died last time — native crashes, ANRs, OEM kills — which CrashGuard's JVM
        // handler can't see. Writes a crash-exit-*.log for any new abnormal exit, picked up below.
        // arm() tags this process with its build sha so the NEXT crash is attributed to the build
        // that actually ran, not whatever is installed when we read it back; collect() reads prior exits.
        ExitInfoCollector.arm(this)
        ExitInfoCollector.collect(this)
        // Flush any pending crash + ride logs to Firestore now that the app is open and likely has
        // internet. Crash logs upload on every build; ride logs are test-channel only.
        DiagnosticsUploader.uploadPending(this)
        // Reinstalling an APK leaves the NotificationListenerService UNBOUND even though the grant
        // persists — which silently breaks now-playing + call mirroring. Ask the system to rebind it
        // on every launch so an over-the-top install self-heals without the user re-toggling access.
        rebindNotificationListener()
        com.sxdpz23.navisarthi.dash.nav.NavigationRouting.install(this)
        // A build's own data links (a local test server, -PobfManifestUrl= and the like), before
        // anything fetches. Blank means the compiled default in NavigationLinks.
        com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.compiled = mapOf(
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.OBF_MANIFEST to BuildConfig.OBF_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.OVERLAY_MANIFEST to BuildConfig.OVERLAY_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.MAXSPEED_MANIFEST to BuildConfig.MAXSPEED_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.ADDRESS_MANIFEST to BuildConfig.ADDRESS_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.PLACES_MANIFEST to BuildConfig.PLACES_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.CELLS_MANIFEST to BuildConfig.CELLS_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.BASEMAP_MANIFEST to BuildConfig.BASEMAP_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.WORLD_BASEMAP to BuildConfig.WORLD_BASEMAP_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.POI_PACK_MANIFEST to BuildConfig.POI_PACK_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.FLOCK_MANIFEST to BuildConfig.FLOCK_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.ROAD_FEATURES_MANIFEST to BuildConfig.ROAD_FEATURES_MANIFEST_URL,
            com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.MAP_FONTS to BuildConfig.MAP_FONTS_URL,
        ).filterValues { it.isNotBlank() }
        // Device memory class first: the Coil cap and the eager-warm decisions read it.
        com.sxdpz23.navisarthi.navigation.ui.MemoryPressure.init(this)
        com.sxdpz23.navisarthi.navigation.ui.SpeechPreload.init(this) // after MemoryPressure: its default reads the RAM tier
        com.sxdpz23.navisarthi.navigation.ui.FullPlaceLoad.init(this)
        // Google-host requests over Chrome's network stack (Cronet), built lazily on the first one.
        // Calibration `useCronet` 0, or an engine that fails to build, leaves them on OkHttp.
        // Before Cronet opens its cache: a due rotation deletes it (Settings > Privacy).
        com.sxdpz23.navisarthi.navigation.web.SessionRotation.init(this)
        com.sxdpz23.navisarthi.navigation.web.GoogleStanding.init(this)
        com.sxdpz23.navisarthi.navigation.web.GoogleTelemetry.init(this)
        com.sxdpz23.navisarthi.navigation.diag.GoogleUsageStore.init(this) // Settings > Privacy > Requests to Google
        // adb-only: `setprop debug.navisarthi.tune.feedDump 1` saves raw review-feed replies to
        // Android/data/com.sxdpz23.navisarthi.navigation/files/feeddump/ (ReviewFeedDebug). Never on otherwise.
        if (com.sxdpz23.navisarthi.navigation.ui.AppTune.localOn("feedDump")) {
            val dir = getExternalFilesDir("feeddump")
            com.sxdpz23.navisarthi.navigation.core.data.google.ReviewFeedDebug.sink = { raw ->
                dir?.let { java.io.File(it, "qv9Egd-${System.currentTimeMillis()}.txt").writeText(raw) }
            }
        }
        // adb-only: `setprop debug.navisarthi.tune.netLog 1` also opens the WebViews to Chrome's remote
        // inspector (adb forward to webview_devtools_remote_<pid>), for reading their real headers.
        if (com.sxdpz23.navisarthi.navigation.ui.AppTune.localOn("netLog")) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        // A test switch for the routes' fallbacks (ValhallaRouter.matchOff). adb only.
        com.sxdpz23.navisarthi.navigation.core.data.ValhallaRouter.matchOff = com.sxdpz23.navisarthi.navigation.ui.AppTune.localOn("noMatch")
        com.sxdpz23.navisarthi.navigation.web.SessionRotation.appJar = http.cookieJar as? com.sxdpz23.navisarthi.navigation.core.di.ResettableCookieJar
        com.sxdpz23.navisarthi.navigation.net.CronetHolder.init(this)
        com.sxdpz23.navisarthi.navigation.core.net.GoogleTransport.interceptor = com.sxdpz23.navisarthi.navigation.net.CronetTransport(http.cookieJar, com.sxdpz23.navisarthi.navigation.web.WebViewCookieJar())
        // Push the device class down to :core, which cannot read an :app holder (same seam as
        // CategoryFilter.enabled). Gates the ambient POI fan-out in GoogleMapsDataSource.
        com.sxdpz23.navisarthi.navigation.core.data.LowRamMode.enabled = com.sxdpz23.navisarthi.navigation.ui.MemoryPressure.lowRam
        com.sxdpz23.navisarthi.navigation.offline.StorageLocation.init(this)
        com.sxdpz23.navisarthi.navigation.offline.RoadNameTileSource.install(this) // street names for LineNamer (issue #478)
        Units.init(this)
        // The desktop window size Google's requests describe: picked once per install, then kept
        // (a size that changed per launch would be its own oddity). See BrowserViewport.
        run {
            val p = getSharedPreferences("navigation_settings", MODE_PRIVATE)
            val idx = p.getInt("browser_viewport", -1).takeIf { it >= 0 }
                ?: kotlin.random.Random.nextInt(com.sxdpz23.navisarthi.navigation.core.data.google.BrowserViewport.CHOICES.size).also { p.edit().putInt("browser_viewport", it).apply() }
            val (w, h) = com.sxdpz23.navisarthi.navigation.core.data.google.BrowserViewport.choice(idx)
            com.sxdpz23.navisarthi.navigation.core.data.google.BrowserViewport.set(w, h)
        }
        com.sxdpz23.navisarthi.navigation.ui.Clock24.refresh(this) // the 12/24-hour clock setting (issue #357); MainActivity refreshes it on resume
        com.sxdpz23.navisarthi.navigation.core.data.transit.Transitous.durationFormatter = { secs -> com.sxdpz23.navisarthi.navigation.ui.formatDuration(secs.toDouble()) }
        com.sxdpz23.navisarthi.navigation.core.i18n.DelayText.formatter = { min ->
            if (min > 0) getString(R.string.transit_min_late, min) else getString(R.string.transit_min_early, -min)
        }
        AppTheme.init(this)
        DynamicColor.init(this)
        AppLocale.init(this) // resolve the app language (system default) → drives the nav-text locale
        Traffic.init(this)
        TransitLayer.init(this)
        com.sxdpz23.navisarthi.navigation.ui.SatelliteLayer.init(this) // persisted satellite-imagery toggle
        com.sxdpz23.navisarthi.navigation.ui.LayersButton.init(this) // persisted show/hide of the map layers button
        com.sxdpz23.navisarthi.navigation.ui.MapTilt.init(this) // two-finger tilt on/off (issue #627)
        com.sxdpz23.navisarthi.navigation.ui.NorthLock.init(this) // Settings > Map "Keep north up"
        com.sxdpz23.navisarthi.navigation.ui.ParkingButton.init(this) // parking button shown with no spot saved (issue #626)
        com.sxdpz23.navisarthi.navigation.ui.LinkAction.init(this) // what a location link from another app does (discussion #640)
        com.sxdpz23.navisarthi.navigation.ui.StartView.init(this) // where the map opens, and the view picked for it
        com.sxdpz23.navisarthi.navigation.ui.NavEndConfirm.init(this) // ask before ending a drive, off by default (issue #624)
        com.sxdpz23.navisarthi.navigation.ui.SpeedDisplay.init(this) // speed + limit badge on/off, phone and car (issue #625)
        com.sxdpz23.navisarthi.navigation.ui.Topography.init(this)
        com.sxdpz23.navisarthi.navigation.ui.Flock.init(this) // load the persisted surveillance-camera toggle (else it read false every launch)
        com.sxdpz23.navisarthi.navigation.ui.SpeedCams.init(this) // same init-or-it-reads-false trap as Flock
        com.sxdpz23.navisarthi.navigation.ui.SpeedCamWarn.init(this) // spoken camera warning (issue #229), off by default
        com.sxdpz23.navisarthi.navigation.ui.SpeedingAlert.init(this) // spoken over-the-limit alert (issue #404), off by default
        com.sxdpz23.navisarthi.navigation.ui.BridgeAlert.init(this) // bridge openings on the route in the Netherlands, on by default
        com.sxdpz23.navisarthi.navigation.ui.BikeSafe.init(this) // bike routes prefer lanes and quiet streets (issue #401), on by default
        // The chooser's sticky avoid toggles, for the nav session's own fetches from the first
        // drive on (a resumed drive or an Android Auto start never opens the phone's chooser).
        getSharedPreferences("navigation_settings", MODE_PRIVATE).let { p ->
            com.sxdpz23.navisarthi.navigation.core.data.RoutingPrefs.avoidTolls = p.getBoolean("avoid_tolls", false)
            com.sxdpz23.navisarthi.navigation.core.data.RoutingPrefs.avoidHighways = p.getBoolean("avoid_highways", false)
            com.sxdpz23.navisarthi.navigation.core.data.RoutingPrefs.avoidFerries = p.getBoolean("avoid_ferries", false)
        }
        com.sxdpz23.navisarthi.navigation.ui.FlockRouteAlert.init(this) // load the persisted "warn about cameras on route" toggle
        com.sxdpz23.navisarthi.navigation.ui.FlockDetour.init(this) // side-street detours around cameras (issue #600), nested under it
        com.sxdpz23.navisarthi.navigation.ui.FlockNavAlert.init(this) // plate-camera card + spoken alert while navigating, off by default
        // Parse the bundled on-device ALPR/Flock camera dataset off the main thread (map layer draws
        // instantly, route counts are reliable), then refresh from the hosted manifest so the data updates
        // without an app release (weekly CI cron re-hosts a newer version; a bump swaps it in on next launch).
        CoroutineScope(Dispatchers.IO).launch {
            // The catalog regions' real boundaries (issue #599): until this lands, region picks
            // fall back to their boxes, which is what they always were.
            com.sxdpz23.navisarthi.navigation.offline.RegionPolys.ensureLoaded(this@NaviSarthiApplication)
            com.sxdpz23.navisarthi.navigation.data.FlockCameras.ensureLoaded(this@NaviSarthiApplication)
            com.sxdpz23.navisarthi.navigation.data.FlockCameras.refresh(this@NaviSarthiApplication, com.sxdpz23.navisarthi.navigation.core.config.NavigationLinks.flockManifest())
        }
        com.sxdpz23.navisarthi.navigation.ui.SimLocation.onChange = {
            locationProvider.pinned = it
            android.util.Log.i("NavigationSim", "simulated location " + if (it != null) "on" else "off")
        }
        com.sxdpz23.navisarthi.navigation.ui.SimLocation.init(this)
        com.sxdpz23.navisarthi.navigation.ui.UiScale.init(this)
        com.sxdpz23.navisarthi.navigation.ui.PageTransitions.init(this)
        com.sxdpz23.navisarthi.navigation.ui.AppFont.init(this) // user-supplied UI font (issue #252)
        com.sxdpz23.navisarthi.navigation.ui.MapColors.init(this)
        com.sxdpz23.navisarthi.navigation.ui.MapButtonShape.init(this) // round or square map buttons (issue #797)
        com.sxdpz23.navisarthi.navigation.ui.TurnArrows.init(this) // arrows on the route at the turns ahead (issue #804)
        com.sxdpz23.navisarthi.navigation.ui.CarSpeed.init(this) // the car's speed badge, off by default
        com.sxdpz23.navisarthi.navigation.ui.LiveReviews.init(this)
        com.sxdpz23.navisarthi.navigation.ui.ShowReviews.init(this)
        com.sxdpz23.navisarthi.navigation.ui.LoadPhotos.init(this)
        com.sxdpz23.navisarthi.navigation.ui.OfflinePlaces.init(this)
        com.sxdpz23.navisarthi.navigation.ui.ReviewsOnTap.init(this)
        com.sxdpz23.navisarthi.navigation.ui.PhotosOnTap.init(this)
        com.sxdpz23.navisarthi.navigation.ui.DetailsRetry.init(this)
        com.sxdpz23.navisarthi.navigation.ui.OtherLocationsAuto.init(this)
        com.sxdpz23.navisarthi.navigation.ui.RouteTrafficOnTap.init(this)
        com.sxdpz23.navisarthi.navigation.core.data.RoutingPrefs.googleTraffic = !com.sxdpz23.navisarthi.navigation.ui.RouteTrafficOnTap.on.value
        com.sxdpz23.navisarthi.navigation.ui.HideAdult.init(this)
        com.sxdpz23.navisarthi.navigation.ui.HideExternalLinks.init(this)
        com.sxdpz23.navisarthi.navigation.ui.GoogleFree.init(this) // "Use Navigation without Google": mirrors into the :core NoGoogle flag
        com.sxdpz23.navisarthi.navigation.ui.Buildings3d.init(this)
        com.sxdpz23.navisarthi.navigation.ui.RouteTrail.init(this)
        com.sxdpz23.navisarthi.navigation.ui.TurnDeclutterPref.init(this)
        com.sxdpz23.navisarthi.navigation.ui.CategoryChipsPref.init(this)
        com.sxdpz23.navisarthi.navigation.ui.QuickChipsPref.init(this) // which quick-category chips show, and their order (issue #779)
        com.sxdpz23.navisarthi.navigation.ui.RoadLabel.init(this)
        com.sxdpz23.navisarthi.navigation.ui.NavNorthUp.init(this)
        com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.init(this) // zoom out to the next turn while driving, off by default (issue #781)
        com.sxdpz23.navisarthi.navigation.ui.PuckStyle.init(this)
        com.sxdpz23.navisarthi.navigation.ui.HouseNumbers.init(this) // house-number zoom gate (issue #329)
        com.sxdpz23.navisarthi.navigation.ui.MapNames.init(this) // which name a map label shows (issue #738)
        com.sxdpz23.navisarthi.navigation.ui.PreferButtons.init(this)
        com.sxdpz23.navisarthi.navigation.ui.ZoomKeys.init(this)
        com.sxdpz23.navisarthi.navigation.ui.PipTurnCard.init(this)
        com.sxdpz23.navisarthi.navigation.ui.PauseInBar.init(this)
        com.sxdpz23.navisarthi.navigation.ui.FasterRouteAuto.init(this)
        com.sxdpz23.navisarthi.navigation.ui.RegionUpdates.init(this)
        com.sxdpz23.navisarthi.navigation.ui.BuildingOverlay.init(this)
        com.sxdpz23.navisarthi.navigation.ui.BuildingDebug.init(this)
        com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.init(this)
        // Mirrors into the :core flag NavEngine reads (issue #596).
        com.sxdpz23.navisarthi.navigation.ui.SpokenRoadNames.init(this)
        com.sxdpz23.navisarthi.navigation.ui.RoadNumbers.init(this) // numbered roads go by their number (issue #836), off by default
        com.sxdpz23.navisarthi.navigation.ui.SpokenDetail.init(this)
        com.sxdpz23.navisarthi.navigation.ui.RoutePicker.init(this)
        com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.init(this)
        com.sxdpz23.navisarthi.navigation.ui.ContactsSearch.init(this) // contacts-in-search toggle (issue #243)
        com.sxdpz23.navisarthi.navigation.diag.NavTrace.init(this) // opt-in nav smoothness trace (issue #251)
        com.sxdpz23.navisarthi.navigation.ui.MapLook.init(this) // which OpenFreeMap look the basemap loads (issue #750); before MapFonts
        com.sxdpz23.navisarthi.navigation.ui.map.MapFonts.init(this) // Roboto basemap glyphs (cached patched style + async refresh)
        Onboarding.init(this)
        // Release notes and APK updates belong to NaviSarthi.
        // Persist any fatal crash (stack trace + breadcrumbs) so it survives the
        // restart and can be exported from Settings → Diagnostics next launch.
        CrashCatcher.install(this) { diag.snapshot() }
    }
    private fun rebindNotificationListener() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
        runCatching {
            if (com.sxdpz23.navisarthi.media.MediaInfoProvider.isAccessGranted(this)) {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, com.sxdpz23.navisarthi.media.NaviSarthiNotificationListener::class.java)
                )
            }
        }
    }
}
