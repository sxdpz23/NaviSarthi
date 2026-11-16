package com.sxdpz23.navisarthi.navigation.core.data

/**
 * Settings > Search > "Find other locations automatically". On (the default): a name search
 * that Google answers with one focused place also asks for its other locations. Off: the results
 * end in a "Show other locations" row and the extra requests run only on a tap. Same seam as [NoGoogle]: the
 * preference lives in `:app` (`com.sxdpz23.navisarthi.navigation.ui.OtherLocationsAuto`) and is pushed down here.
 */
object OtherLocations {
    @Volatile var auto: Boolean = true
}
