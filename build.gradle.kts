plugins {
    alias(navigationLibs.plugins.kotlin.ksp) apply false
    alias(navigationLibs.plugins.hilt) apply false
    alias(navigationLibs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
