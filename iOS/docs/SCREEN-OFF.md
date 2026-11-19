# Experimental screen-off projection

Foreground is default. Opt-in iOS 26 BGContinuedProcessingTask uses finite
user-started trip work, immediate fail strategy, real progress and cancellation.
Xcode 26/Swift 6.2 is required for that conditional path. No fake audio or GPU
background entitlement. Granted background frames use CPU roads from Nav rather
than assuming locked MapKit snapshots. Without a grant, background stops streaming.

This is not proof of locked-phone VideoToolbox/Wi-Fi endurance. OS may refuse/end
work. Test prolonged lock/unlock, thermal/power conditions, GPS/network loss,
refusal/cancellation, arrival and stop on real iOS 26/Dash. Screen-off remains
UNVERIFIED. Location/BLE modes do not grant unlimited video/network runtime.
Reference: https://developer.apple.com/documentation/backgroundtasks/bgcontinuedprocessingtask
